package uz.familymens.store.model;

import java.util.List;
import java.util.Locale;

public enum Category {
    SHOES("krossovka", "krasovka", "oyoq kiyim", "botinka", "кроссовк", "кеды", "обувь", "ботинк", "sneaker", "shoes", "slide", "шлепанц", "shlepan"),
    OUTERWEAR("kurtka", "куртк", "parka", "парк", "jacket", "palto", "пальто", "puffer", "пуховик", "duck down", "pat ", "ветровк", "windbreaker", "жилет", "jilet", "vest"),
    HOODIES("xudi", "hudi", "худи", "hoodie", "svitshot", "свитшот", "sweatshirt", "fleece", "флис", "sviter", "свитер", "kofta", "кофт", "zip"),
    PANTS("shim", "брюк", "штаны", "pants", "jogger", "джоггер", "sweatpants", "шорт", "shorts", "short"),
    TSHIRTS("futbolka", "футболк", "t-shirt", "tshirt", "tee", "polo", "поло", "mayka", "майк"),
    TRACKSUITS("kostyum", "костюм", "tracksuit", "sport kostyum", "komplekt", "комплект"),
    ACCESSORIES("sumka", "сумк", "bag", "kepka", "кепк", "cap", "рюкзак", "ryukzak", "backpack", "носк", "socks", "paypoq", "шапк", "shapka", "beanie", "perchatka", "перчатк");

    private final List<String> keywords;

    Category(String... keywords) {
        this.keywords = List.of(keywords);
    }

    /** URL va i18n kalitlari uchun: "shoes", "outerwear" ... */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Category fromKey(String key) {
        if (key == null || key.isBlank()) return null;
        for (Category c : values()) {
            if (c.key().equalsIgnoreCase(key)) return c;
        }
        return null;
    }

    /** Post matnidan kategoriyani taxmin qiladi (topilmasa null). */
    public static Category detect(String text) {
        if (text == null) return null;
        String t = text.toLowerCase(Locale.ROOT);
        Category best = null;
        int bestPos = Integer.MAX_VALUE;
        for (Category c : values()) {
            for (String k : c.keywords) {
                int pos = t.indexOf(k);
                if (pos >= 0 && pos < bestPos) {
                    bestPos = pos;
                    best = c;
                }
            }
        }
        return best;
    }
}
