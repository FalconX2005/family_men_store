package uz.familymens.store.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oddiy "sirpanuvchi oyna" cheklovchi (xotirada). Login brute-force va buyurtma spamidan himoya.
 * Kalit sifatida odatda "turi:IP" ishlatiladi.
 */
@Component
public class RateLimiter {

    private static final Duration MAX_WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** Oxirgi {@code window} ichidagi urinishlar soni. */
    public int count(String key, Duration window) {
        Deque<Long> q = hits.get(key);
        if (q == null) return 0;
        synchronized (q) {
            prune(q, window);
            return q.size();
        }
    }

    public void record(String key) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(System.currentTimeMillis());
            while (q.size() > 1000) q.pollFirst();
        }
    }

    /** Chegaradan oshmagan bo'lsa urinishni hisobga oladi va true qaytaradi. */
    public boolean tryAcquire(String key, int max, Duration window) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, window);
            if (q.size() >= max) return false;
            q.addLast(System.currentTimeMillis());
            return true;
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    private static void prune(Deque<Long> q, Duration window) {
        long cutoff = System.currentTimeMillis() - window.toMillis();
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
    }

    /** Eskirgan yozuvlarni tozalaydi — xotira o'smasligi uchun. */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void cleanup() {
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), MAX_WINDOW);
                return e.getValue().isEmpty();
            }
        });
    }
}
