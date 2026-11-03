package uz.familymens.store.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.familymens.store.model.Brands;
import uz.familymens.store.model.SiteSetting;
import uz.familymens.store.repository.SiteSettingRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sayt dizayni sozlamalari: bosh sahifa rasmlari va matnlari.
 * Qiymat berilmagan bo'lsa, shablon standart matn/rasmni ishlatadi.
 * Sozlamalar xotirada keshlanadi — har sahifada bazaga murojaat qilinmaydi.
 */
@Service
public class SiteSettingsService {

    public static final int MAX_TEXT = 1000;

    /** Ikki tilli matnlar: kalit.uz / kalit.ru */
    public static final List<TextField> TEXTS = List.of(
            new TextField("topbar", "Yuqoridagi e'lon qatori", false),
            new TextField("hero.eyebrow", "Bosh sahifa: kichik sarlavha", false),
            new TextField("hero.title1", "Bosh sahifa: sarlavha 1-qism", false),
            new TextField("hero.title2", "Bosh sahifa: sarlavha 2-qism (oltin, kursiv)", false),
            new TextField("hero.title3", "Bosh sahifa: sarlavha 3-qism", false),
            new TextField("hero.text", "Bosh sahifa: matn", true),
            new TextField("about.title", "\"Qanday ishlaymiz\": sarlavha", false),
            new TextField("about.text1", "\"Qanday ishlaymiz\": 1-paragraf", true),
            new TextField("about.text2", "\"Qanday ishlaymiz\": 2-paragraf", true),
            new TextField("footer.title", "Pastki qism: sarlavha", false)
    );

    /** Tilga bog'liq bo'lmagan qiymatlar */
    public static final List<TextField> VALUES = List.of(
            new TextField("stat1.value", "Statistika 1 (masalan 10K+)", false),
            new TextField("stat2.value", "Statistika 2 (masalan 3)", false),
            new TextField("stat3.value", "Statistika 3 (masalan 7–15)", false)
    );

    public static final List<TextField> IMAGES;

    static {
        List<TextField> imgs = new ArrayList<>();
        imgs.add(new TextField("image.hero", "Bosh sahifa katta rasmi (tavsiya: 2000×1200, gorizontal)", false));
        imgs.add(new TextField("image.about", "\"Qanday ishlaymiz\" rasmi (tavsiya: vertikal)", false));
        for (String b : Brands.MAIN) {
            imgs.add(new TextField("image.brand." + b, b + " kartasi rasmi (bo'sh bo'lsa — oxirgi mahsulot rasmi)", false));
        }
        IMAGES = List.copyOf(imgs);
    }

    public static final String[] LANGS = {"uz", "ru"};

    private final SiteSettingRepository repository;
    private final FileStorageService files;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public SiteSettingsService(SiteSettingRepository repository, FileStorageService files) {
        this.repository = repository;
        this.files = files;
    }

    private Map<String, String> values() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    repository.findAll().forEach(s -> {
                        if (s.getValue() != null) cache.put(s.getKey(), s.getValue());
                    });
                    loaded = true;
                }
            }
        }
        return cache;
    }

    /** Shablonlar uchun: ${site.text('hero.title1', lang)} ?: #{hero.title1} */
    public String text(String key, String lang) {
        return values().get(key + "." + lang);
    }

    public String value(String key) {
        return values().get(key);
    }

    public String image(String key) {
        return values().get("image." + key);
    }

    public Map<String, String> all() {
        return Collections.unmodifiableMap(values());
    }

    /** Barcha ruxsat etilgan matn kalitlari (kalit.uz, kalit.ru, stat...) */
    public static Set<String> allowedTextKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (TextField f : TEXTS) for (String l : LANGS) keys.add(f.key() + "." + l);
        for (TextField f : VALUES) keys.add(f.key());
        return keys;
    }

    public static Set<String> allowedImageKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (TextField f : IMAGES) keys.add(f.key());
        return keys;
    }

    /**
     * @param texts   kalit -> matn (bo'sh — standartga qaytadi)
     * @param uploads kalit -> yangi rasm fayli
     * @param resets  standartga qaytariladigan rasm kalitlari
     */
    @Transactional
    public void save(Map<String, String> texts, Map<String, MultipartFile> uploads, Set<String> resets) {
        Set<String> allowedTexts = allowedTextKeys();
        Set<String> allowedImages = allowedImageKeys();

        // Avval barcha rasmlarni tekshirib saqlaymiz — xato bo'lsa hech narsa o'zgarmaydi
        Map<String, String> newImages = new LinkedHashMap<>();
        for (var e : uploads.entrySet()) {
            if (!allowedImages.contains(e.getKey()) || e.getValue() == null || e.getValue().isEmpty()) continue;
            newImages.put(e.getKey(), files.store(e.getValue()));
        }

        for (var e : texts.entrySet()) {
            if (!allowedTexts.contains(e.getKey())) continue;
            String v = e.getValue() == null ? "" : e.getValue().strip();
            if (v.length() > MAX_TEXT) v = v.substring(0, MAX_TEXT);
            put(e.getKey(), v.isEmpty() ? null : v);
        }
        for (String key : resets) {
            if (!allowedImages.contains(key) || newImages.containsKey(key)) continue;
            String old = values().get(key);
            put(key, null);
            files.deleteIfLocal(old);
        }
        for (var e : newImages.entrySet()) {
            String old = values().get(e.getKey());
            put(e.getKey(), e.getValue());
            files.deleteIfLocal(old);
        }
    }

    private void put(String key, String value) {
        if (value == null) {
            repository.deleteById(key);
            values().remove(key);
        } else {
            repository.save(new SiteSetting(key, value));
            values().put(key, value);
        }
    }

    /** @param multiline true — textarea, false — bir qatorli input */
    public record TextField(String key, String label, boolean multiline) {
    }
}
