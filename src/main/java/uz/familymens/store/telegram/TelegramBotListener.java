package uz.familymens.store.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.familymens.store.config.StoreProperties;

import java.util.Locale;
import java.util.concurrent.*;

/**
 * Darhol yangilash: bot kanalda admin bo'lsa, Telegram har bir yangi/tahrirlangan postni
 * botga yuboradi (long polling — ommaviy URL yoki HTTPS kerak emas). Post kelishi bilan
 * sinxronizatsiya ishga tushadi.
 * <p>
 * Albom bir nechta xabar bo'lib keladi, shuning uchun bir necha soniya kutib (debounce),
 * bitta sinxronizatsiya qilinadi. t.me/s sahifasi biroz kechikib yangilanishi mumkinligi
 * uchun bir daqiqadan so'ng yana bir marta tekshiriladi.
 */
@Component
public class TelegramBotListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotListener.class);
    private static final int LONG_POLL_SECONDS = 50;
    private static final long DEBOUNCE_SECONDS = 6;
    private static final long FOLLOW_UP_SECONDS = 60;

    private final StoreProperties props;
    private final TelegramSyncService sync;
    private final RestClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tg-instant-sync");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private volatile Thread pollThread;
    private volatile ScheduledFuture<?> pendingSync;
    private volatile ScheduledFuture<?> pendingFollowUp;
    private volatile long lastEventAt;

    public TelegramBotListener(StoreProperties props, TelegramSyncService sync) {
        this.props = props;
        this.sync = sync;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout((LONG_POLL_SECONDS + 15) * 1000);
        this.client = RestClient.builder()
                .baseUrl("https://api.telegram.org")
                .requestFactory(factory)
                .build();
    }

    private boolean enabled() {
        return props.sync().enabled() && props.sync().instant()
                && props.telegramBotToken() != null && !props.telegramBotToken().isBlank();
    }

    @Override
    public void start() {
        if (!enabled()) {
            log.info("Instant Telegram sync is off (no bot token or store.sync.instant=false)");
            return;
        }
        running = true;
        pollThread = new Thread(this::pollLoop, "tg-bot-poll");
        pollThread.setDaemon(true);
        pollThread.start();
        log.info("Instant Telegram sync: listening for posts in @{}", props.telegramChannel());
    }

    @Override
    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        scheduler.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Oxirgi post hodisasi vaqti (admin panel uchun), 0 — hali bo'lmagan. */
    public long lastEventAt() {
        return lastEventAt;
    }

    public boolean listening() {
        return running;
    }

    /**
     * Bot kanalda admin'mi? (admin panelda ko'rsatish uchun)
     *
     * @return "administrator", "member", "left" ... yoki xatolik bo'lsa null
     */
    public String botStatusInChannel() {
        if (!enabled()) return null;
        try {
            long botId = Long.parseLong(props.telegramBotToken().substring(0, props.telegramBotToken().indexOf(':')));
            JsonNode res = client.get()
                    .uri("/bot{token}/getChatMember?chat_id=@{channel}&user_id={id}",
                            props.telegramBotToken(), props.telegramChannel(), botId)
                    .retrieve()
                    .onStatus(s -> s.is4xxClientError(), (req, resp) -> { })
                    .body(JsonNode.class);
            if (res == null || !res.path("ok").asBoolean()) return "left";
            return res.path("result").path("status").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void pollLoop() {
        long offset = 0;
        int failures = 0;
        while (running) {
            final long currentOffset = offset;
            try {
                JsonNode res = client.get()
                        .uri(b -> b.path("/bot{token}/getUpdates")
                                .queryParam("timeout", LONG_POLL_SECONDS)
                                .queryParam("offset", "{offset}")
                                .queryParam("allowed_updates", "{allowed}")
                                .build(props.telegramBotToken(), currentOffset,
                                        "[\"channel_post\",\"edited_channel_post\",\"message\"]"))
                        .retrieve()
                        .body(JsonNode.class);
                failures = 0;
                if (res == null || !res.path("ok").asBoolean()) continue;
                for (JsonNode upd : res.path("result")) {
                    offset = Math.max(offset, upd.path("update_id").asLong() + 1);
                    handle(upd);
                }
            } catch (Exception e) {
                if (!running) break;
                failures++;
                // 409 — boshqa joyda ham getUpdates/webhook ishlatilmoqda
                log.warn("Telegram getUpdates failed ({}): {}", failures, e.getMessage());
                sleepQuietly(Math.min(60, 5L * failures));
            }
        }
    }

    private void handle(JsonNode upd) {
        JsonNode post = upd.has("channel_post") ? upd.get("channel_post") : upd.get("edited_channel_post");
        if (post == null) return; // oddiy xabarlar (masalan /start) — e'tibor bermaymiz
        String username = post.path("chat").path("username").asText("");
        if (!username.toLowerCase(Locale.ROOT).equals(props.telegramChannel().toLowerCase(Locale.ROOT))) return;

        lastEventAt = System.currentTimeMillis();
        log.info("Telegram: {} post #{} in @{}", upd.has("channel_post") ? "new" : "edited",
                post.path("message_id").asLong(), username);
        scheduleSync();
    }

    private synchronized void scheduleSync() {
        if (pendingSync != null) pendingSync.cancel(false);
        if (pendingFollowUp != null) pendingFollowUp.cancel(false);
        pendingSync = scheduler.schedule(this::runSync, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        pendingFollowUp = scheduler.schedule(this::runSync, FOLLOW_UP_SECONDS, TimeUnit.SECONDS);
    }

    private void runSync() {
        try {
            if (sync.isRunning()) {
                // boshqa sinxronizatsiya ketmoqda — birozdan so'ng qayta urinamiz
                scheduler.schedule(this::runSync, 10, TimeUnit.SECONDS);
                return;
            }
            var status = sync.syncLatest();
            log.info("Instant sync: {}", status.message());
        } catch (Exception e) {
            log.warn("Instant sync failed: {}", e.toString());
        }
    }

    private static void sleepQuietly(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
