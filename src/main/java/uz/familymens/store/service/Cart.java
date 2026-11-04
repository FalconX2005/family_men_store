package uz.familymens.store.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Har bir foydalanuvchi sessiyasi uchun alohida savatcha.
 * Faqat productId + o'lcham + soni saqlanadi; narx va nom har safar bazadan olinadi.
 */
@Component
@SessionScope
public class Cart implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int MAX_QTY = 20;
    public static final int MAX_LINES = 30;

    private final List<Entry> entries = new ArrayList<>();

    public synchronized void add(long productId, String size, int qty) {
        for (Entry e : entries) {
            if (e.productId == productId && e.size.equals(size)) {
                e.quantity = Math.min(MAX_QTY, e.quantity + qty);
                return;
            }
        }
        if (entries.size() >= MAX_LINES) return; // sessiyani to'ldirib yuborishdan himoya
        entries.add(new Entry(productId, size, Math.min(MAX_QTY, qty)));
    }

    public synchronized void setQuantity(int index, int qty) {
        if (index < 0 || index >= entries.size()) return;
        if (qty <= 0) entries.remove(index);
        else entries.get(index).quantity = Math.min(MAX_QTY, qty);
    }

    public synchronized void remove(int index) {
        if (index >= 0 && index < entries.size()) entries.remove(index);
    }

    public synchronized void clear() {
        entries.clear();
    }

    /** Faqat berilgan (faol) mahsulotlarga tegishli yozuvlarni qoldiradi. */
    public synchronized void retainProducts(Set<Long> activeIds) {
        entries.removeIf(e -> !activeIds.contains(e.productId));
    }

    public synchronized List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized int count() {
        return entries.stream().mapToInt(e -> e.quantity).sum();
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public static final class Entry implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final long productId;
        private final String size;
        private int quantity;

        Entry(long productId, String size, int quantity) {
            this.productId = productId;
            this.size = size;
            this.quantity = quantity;
        }

        public long productId() { return productId; }
        public String size() { return size; }
        public int quantity() { return quantity; }
    }
}
