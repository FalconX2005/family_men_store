package uz.familymens.store.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_active_published", columnList = "active,publishedAt"),
        @Index(name = "idx_product_brand", columnList = "brand")
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(nullable = false)
    private String nameUz;

    @Column(nullable = false)
    private String nameRu;

    @Column(length = 4000)
    private String descriptionUz;

    @Column(length = 4000)
    private String descriptionRu;

    /** Ixtiyoriy: Telegram postlaridan har doim ham aniqlab bo'lmaydi */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ProductTag tag;

    /** Narx AQSH dollarida (kargo/yetkazib berish alohida) */
    @Column(nullable = false)
    private long price;

    /** Chizilgan (asl) narx, dollarda — ixtiyoriy */
    private Long oldPrice;

    /** Vergul bilan: "S,M,L,XL". Bo'sh bo'lsa — mijoz o'lchamini o'zi yozadi */
    @Column(length = 255)
    private String sizes;

    /** Rasmlar tartib bilan; birinchisi — asosiy rasm */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", length = 1000)
    @BatchSize(size = 100)
    private List<String> images = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    // ===== Telegram kanal bilan bog'lanish =====

    /** Kanal postining raqami (t.me/kanal/12345). Qo'lda qo'shilgan mahsulotda null. */
    @Column(unique = true)
    private Long telegramPostId;

    /** false bo'lsa — kanal o'zgarishlari bu mahsulotga ta'sir qilmaydi */
    @Column(nullable = false)
    private boolean telegramSync = true;

    @Column(length = 64)
    private String captionHash;

    /** Saralash uchun: post sanasi yoki qo'shilgan vaqt */
    @Column(nullable = false)
    private LocalDateTime publishedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (publishedAt == null) publishedAt = createdAt;
    }

    // ===== Shablonlar uchun yordamchi metodlar =====

    public String name(String lang) {
        return "ru".equals(lang) ? nameRu : nameUz;
    }

    public String description(String lang) {
        String d = "ru".equals(lang) ? descriptionRu : descriptionUz;
        return d == null || d.isBlank() ? descriptionUz : d;
    }

    public String getImageUrl() {
        return images.isEmpty() ? null : images.get(0);
    }

    public List<String> sizeList() {
        if (sizes == null || sizes.isBlank()) return List.of();
        return Arrays.stream(sizes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** O'lcham ro'yxati bo'lmasa, mijoz yozgan istalgan o'lcham qabul qilinadi. */
    public boolean acceptsSize(String size) {
        if (size == null || size.isBlank()) return false;
        List<String> list = sizeList();
        return list.isEmpty() ? size.trim().length() <= 20 : list.contains(size);
    }

    public boolean onSale() {
        return oldPrice != null && oldPrice > price;
    }

    public boolean fromTelegram() {
        return telegramPostId != null;
    }

    // ===== Getters / setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getNameUz() { return nameUz; }
    public void setNameUz(String nameUz) { this.nameUz = nameUz; }
    public String getNameRu() { return nameRu; }
    public void setNameRu(String nameRu) { this.nameRu = nameRu; }
    public String getDescriptionUz() { return descriptionUz; }
    public void setDescriptionUz(String descriptionUz) { this.descriptionUz = descriptionUz; }
    public String getDescriptionRu() { return descriptionRu; }
    public void setDescriptionRu(String descriptionRu) { this.descriptionRu = descriptionRu; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public ProductTag getTag() { return tag; }
    public void setTag(ProductTag tag) { this.tag = tag; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public Long getOldPrice() { return oldPrice; }
    public void setOldPrice(Long oldPrice) { this.oldPrice = oldPrice; }
    public String getSizes() { return sizes; }
    public void setSizes(String sizes) { this.sizes = sizes; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = new ArrayList<>(images); }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getTelegramPostId() { return telegramPostId; }
    public void setTelegramPostId(Long telegramPostId) { this.telegramPostId = telegramPostId; }
    public boolean isTelegramSync() { return telegramSync; }
    public void setTelegramSync(boolean telegramSync) { this.telegramSync = telegramSync; }
    public String getCaptionHash() { return captionHash; }
    public void setCaptionHash(String captionHash) { this.captionHash = captionHash; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
