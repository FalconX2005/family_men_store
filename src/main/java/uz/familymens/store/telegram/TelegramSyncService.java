package uz.familymens.store.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import uz.familymens.store.config.StoreProperties;
import uz.familymens.store.model.Brands;
import uz.familymens.store.model.Category;
import uz.familymens.store.model.Product;
import uz.familymens.store.model.ProductTag;
import uz.familymens.store.repository.ProductRepository;
import uz.familymens.store.service.FileStorageService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Telegram kanal -> sayt sinxronizatsiyasi.
 * <ul>
 *   <li>Yangi post (rasm + narx) — yangi mahsulot</li>
 *   <li>Post tahrirlansa (narx o'zgarsa) — mahsulot yangilanadi</li>
 *   <li>Post kanaldan o'chirilsa — mahsulot saytdan yashiriladi</li>
 * </ul>
 */
@Service
public class TelegramSyncService {

    private static final Logger log = LoggerFactory.getLogger(TelegramSyncService.class);
    private static final long PAGE_DELAY_MS = 1500;
    private static final int MAX_HISTORY_PAGES = 1000;

    /** Izohdan olib tashlanadigan qatorlar: aloqa ma'lumotlari, "Narxi ..." */
    private static final Pattern NOISE_LINE = Pattern.compile(
            "(?i)^(buyurtma uchun|для заказа|заказ|tel|тел|narxi|цена|price)\\b.*|^@\\w+.*|^\\+?[\\d\\s()-]{7,}$|^https?://\\S+$");

    private final TelegramChannelClient client;
    private final ProductRepository products;
    private final FileStorageService files;
    private final StoreProperties props;
    private final TransactionTemplate tx;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile SyncStatus lastStatus = SyncStatus.never();

    public TelegramSyncService(TelegramChannelClient client, ProductRepository products, FileStorageService files,
                               StoreProperties props, TransactionTemplate tx) {
        this.client = client;
        this.products = products;
        this.files = files;
        this.props = props;
        this.tx = tx;
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "${store.sync.interval:PT10M}")
    public void scheduled() {
        if (!props.sync().enabled()) return;
        syncLatest();
    }

    /** Eng yangi postlarni tekshiradi. Birinchi marta — initial-pages sahifa yuklanadi. */
    public SyncStatus syncLatest() {
        boolean firstRun = products.countByTelegramPostIdIsNotNull() == 0;
        int maxPages = firstRun ? props.sync().initialPages() : props.sync().maxPagesPerRun();
        return run(null, maxPages, !firstRun, null);
    }

    /** Saytdagi eng eski postdan ham oldingi postlarni yuklaydi. */
    public SyncStatus loadOlder(int pages) {
        Long min = products.minTelegramPostId();
        return run(min, pages, false, null);
    }

    /**
     * Berilgan sanadan keyin qo'yilgan barcha postlarni yuklaydi: saytdagi eng eski postdan
     * boshlab orqaga yuradi va shu sanadan oldingi postlarga yetganda to'xtaydi.
     * Uzoq davom etishi mumkin — fonda chaqiring.
     */
    public SyncStatus loadSince(LocalDate since) {
        Long min = products.minTelegramPostId();
        return run(min, MAX_HISTORY_PAGES, false, since.atStartOfDay());
    }

    public SyncStatus status() {
        return lastStatus;
    }

    public boolean isRunning() {
        return lock.isLocked();
    }

    /**
     * @param notBefore null bo'lmasa — shu vaqtdan oldingi postlar o'tkazib yuboriladi va
     *                  sahifa shunday postlarga yetganda yuklash to'xtaydi
     */
    private SyncStatus run(Long before, int maxPages, boolean stopWhenNothingNew, LocalDateTime notBefore) {
        if (!lock.tryLock()) {
            return lastStatus.withMessage("Sinxronizatsiya hozir ishlayapti, birozdan so'ng qayta urinib ko'ring");
        }
        Counter c = new Counter();
        try {
            Long cursor = before;
            for (int page = 0; page < maxPages; page++) {
                TelegramChannelClient.ChannelPage p = client.fetch(cursor);
                if (p.isEmpty()) break;
                c.pages++;
                boolean anyNew = processPage(p, c, notBefore);
                if (notBefore != null) {
                    lastStatus = new SyncStatus(LocalDateTime.now(), true, c.pages, c.created, c.updated, c.hidden,
                            String.format("Yuklanmoqda… %d sahifa, %d yangi mahsulot (hozir: %s)",
                                    c.pages, c.created, oldestDate(p)));
                }
                boolean reachedCutoff = notBefore != null && p.posts().stream()
                        .anyMatch(x -> x.publishedAt() != null && x.publishedAt().isBefore(notBefore));
                if (p.older() == null || reachedCutoff || (stopWhenNothingNew && !anyNew)) break;
                cursor = p.older();
                Thread.sleep(PAGE_DELAY_MS);
            }
            lastStatus = new SyncStatus(LocalDateTime.now(), true, c.pages, c.created, c.updated, c.hidden,
                    String.format("%d sahifa tekshirildi: %d yangi, %d yangilandi, %d yashirildi",
                            c.pages, c.created, c.updated, c.hidden));
            if (c.created + c.updated + c.hidden > 0) log.info("Telegram sync: {}", lastStatus.message());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Telegram sync failed: {}", e.toString());
            lastStatus = new SyncStatus(LocalDateTime.now(), false, c.pages, c.created, c.updated, c.hidden,
                    "Xatolik: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return lastStatus;
    }

    /** @return sahifada yangi yoki o'zgargan post bo'lganmi */
    private boolean processPage(TelegramChannelClient.ChannelPage page, Counter c, LocalDateTime notBefore) {
        boolean changed = false;
        Set<Long> seen = new HashSet<>();
        for (ChannelPost post : page.posts()) {
            seen.add(post.id());
            if (!post.isProduct()) continue; // so'rovnoma, e'lon va h.k.
            if (notBefore != null && post.publishedAt() != null && post.publishedAt().isBefore(notBefore)) continue;
            try {
                Boolean created = upsert(post);
                if (created == null) continue;
                changed = true;
                if (created) c.created++; else c.updated++;
            } catch (Exception e) {
                log.warn("Could not import post #{}: {}", post.id(), e.toString());
            }
        }
        // Sahifa oralig'ida bo'lib, endi ko'rinmayotgan postlar — kanaldan o'chirilgan
        Integer hidden = tx.execute(s -> {
            int n = 0;
            for (Product p : products.findByTelegramPostIdBetween(page.minId(), page.maxId())) {
                if (p.isTelegramSync() && p.isActive() && !seen.contains(p.getTelegramPostId())) {
                    p.setActive(false);
                    n++;
                }
            }
            return n;
        });
        c.hidden += hidden == null ? 0 : hidden;
        return changed;
    }

    /** @return true — yaratildi, false — yangilandi, null — o'zgarish yo'q */
    private Boolean upsert(ChannelPost post) {
        Optional<Product> existing = products.findByTelegramPostId(post.id());
        String hash = hash(post.caption() + "|" + post.price() + "|" + post.oldPrice() + "|" + post.imageUrls().size());

        if (existing.isPresent()) {
            Product p = existing.get();
            if (!p.isTelegramSync()) return null;
            boolean missingImages = p.getImages().size() < post.imageUrls().size();
            if (hash.equals(p.getCaptionHash()) && !missingImages) {
                if (p.isActive()) return null;
                // Post yana ko'rindi (avval sinxronizatsiya yashirgan edi) — qaytadan ko'rsatamiz.
                // Admin qo'lda yashirganda telegramSync=false bo'ladi, shuning uchun bu yerga kelmaydi.
                tx.executeWithoutResult(s -> products.findById(p.getId()).ifPresent(f -> f.setActive(true)));
                return false;
            }
            List<String> downloaded = p.getImages().size() == post.imageUrls().size()
                    ? null
                    : downloadImages(post);
            // Yangi yuklangan rasmlar avvalgidan kam bo'lsa — eskilarini qoldiramiz
            List<String> newImages = downloaded != null && downloaded.size() > p.getImages().size() ? downloaded : null;
            tx.executeWithoutResult(s -> {
                Product fresh = products.findById(p.getId()).orElseThrow();
                List<String> old = new ArrayList<>(fresh.getImages());
                applyCaption(fresh, post);
                if (newImages != null) fresh.setImages(newImages);
                fresh.setCaptionHash(hash);
                fresh.setActive(true);
                products.save(fresh);
                if (newImages != null) old.stream().filter(u -> !newImages.contains(u)).forEach(files::deleteIfLocal);
            });
            return false;
        }

        List<String> images = downloadImages(post);
        if (images.isEmpty()) return null;
        tx.executeWithoutResult(s -> {
            Product p = new Product();
            p.setTelegramPostId(post.id());
            p.setPublishedAt(post.publishedAt() != null ? post.publishedAt() : LocalDateTime.now());
            p.setBrand(Brands.detect(post.caption()));
            p.setCategory(Category.detect(post.caption()));
            String title = title(post.caption(), p.getBrand());
            p.setNameUz(title);
            p.setNameRu(title);
            applyCaption(p, post);
            p.setImages(images);
            p.setCaptionHash(hash);
            products.save(p);
        });
        return true;
    }

    private void applyCaption(Product p, ChannelPost post) {
        p.setPrice(post.price());
        p.setOldPrice(post.oldPrice());
        p.setTag(post.oldPrice() != null ? ProductTag.SALE : null);
        String desc = cleanDescription(post.caption());
        p.setDescriptionUz(desc);
        p.setDescriptionRu(desc);
    }

    private List<String> downloadImages(ChannelPost post) {
        List<String> result = new ArrayList<>();
        int i = 0;
        for (String url : post.imageUrls()) {
            String name = "tg/" + post.id() + "-" + (i++);
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    result.add(files.storeFromUrl(url, name));
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return result;
                } catch (Exception e) {
                    if (attempt == 3) {
                        log.warn("Image download failed for post #{}: {}", post.id(), e.toString());
                    } else {
                        try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Birinchi mazmunli qator — mahsulot nomi ("Adidas original Amerika"). */
    static String title(String caption, String brand) {
        for (String line : caption.split("\n")) {
            String l = line.strip();
            if (!l.isEmpty() && !NOISE_LINE.matcher(l).matches()) {
                return l.length() > 120 ? l.substring(0, 117) + "…" : l;
            }
        }
        return brand + " original";
    }

    /** Aloqa qatorlari va narx qatorini olib tashlaydi — qolgani tavsif. */
    static String cleanDescription(String caption) {
        List<String> keep = new ArrayList<>();
        boolean first = true;
        for (String line : caption.split("\n")) {
            String l = line.strip();
            if (l.isEmpty() || NOISE_LINE.matcher(l).matches()) continue;
            if (first) { first = false; continue; } // birinchi qator — nom
            keep.add(l);
        }
        return keep.isEmpty() ? null : String.join("\n", keep);
    }

    private static String oldestDate(TelegramChannelClient.ChannelPage p) {
        return p.posts().stream().map(ChannelPost::publishedAt).filter(Objects::nonNull).min(LocalDateTime::compareTo)
                .map(d -> d.toLocalDate().toString()).orElse("?");
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Counter {
        int pages, created, updated, hidden;
    }

    public record SyncStatus(LocalDateTime time, boolean ok, int pages, int created, int updated, int hidden,
                             String message) {
        static SyncStatus never() {
            return new SyncStatus(null, true, 0, 0, 0, 0, "Hali ishga tushmagan");
        }

        SyncStatus withMessage(String m) {
            return new SyncStatus(time, ok, pages, created, updated, hidden, m);
        }
    }
}
