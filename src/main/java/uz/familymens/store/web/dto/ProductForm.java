package uz.familymens.store.web.dto;

import jakarta.validation.constraints.*;
import uz.familymens.store.model.Category;
import uz.familymens.store.model.Product;
import uz.familymens.store.model.ProductTag;

import java.util.Arrays;
import java.util.List;

public class ProductForm {

    private Long id;

    @NotBlank(message = "Brend nomini kiriting")
    @Size(max = 80)
    private String brand;

    @NotBlank(message = "Nomini kiriting (UZ)")
    @Size(max = 255)
    private String nameUz;

    @NotBlank(message = "Nomini kiriting (RU)")
    @Size(max = 255)
    private String nameRu;

    @Size(max = 4000)
    private String descriptionUz;

    @Size(max = 4000)
    private String descriptionRu;

    /** Ixtiyoriy */
    private Category category;

    private ProductTag tag;

    @NotNull(message = "Narxni kiriting")
    @Positive(message = "Narx musbat bo'lishi kerak")
    @Max(value = 100_000, message = "Narx $100 000 dan oshmasligi kerak")
    private Long price;

    @Positive(message = "Eski narx musbat bo'lishi kerak")
    @Max(value = 100_000, message = "Narx $100 000 dan oshmasligi kerak")
    private Long oldPrice;

    /** Ixtiyoriy. Bo'sh bo'lsa mijoz o'lchamni o'zi yozadi */
    @Size(max = 255)
    @Pattern(regexp = "^$|^[^,]+(,[^,]+)*$", message = "O'lchamlarni vergul bilan ajrating: S,M,L")
    private String sizes;

    /** Har bir qatorda bitta rasm havolasi; birinchisi — asosiy */
    @Size(max = 10000)
    private String imageUrls;

    private boolean active = true;

    private boolean telegramSync = true;

    private Long telegramPostId;

    public static ProductForm from(Product p) {
        ProductForm f = new ProductForm();
        f.id = p.getId();
        f.brand = p.getBrand();
        f.nameUz = p.getNameUz();
        f.nameRu = p.getNameRu();
        f.descriptionUz = p.getDescriptionUz();
        f.descriptionRu = p.getDescriptionRu();
        f.category = p.getCategory();
        f.tag = p.getTag();
        f.price = p.getPrice();
        f.oldPrice = p.getOldPrice();
        f.sizes = p.getSizes();
        f.imageUrls = String.join("\n", p.getImages());
        f.active = p.isActive();
        f.telegramSync = p.isTelegramSync();
        f.telegramPostId = p.getTelegramPostId();
        return f;
    }

    public void applyTo(Product p) {
        p.setBrand(brand.trim());
        p.setNameUz(nameUz.trim());
        p.setNameRu(nameRu.trim());
        p.setDescriptionUz(blankToNull(descriptionUz));
        p.setDescriptionRu(blankToNull(descriptionRu));
        p.setCategory(category);
        p.setTag(tag);
        p.setPrice(price);
        p.setOldPrice(oldPrice);
        p.setSizes(sizes == null || sizes.isBlank() ? null : String.join(",", sizes.replace(" ", "").split(",")));
        p.setImages(imageList());
        p.setActive(active);
        p.setTelegramSync(telegramSync);
    }

    public List<String> imageList() {
        if (imageUrls == null) return List.of();
        return Arrays.stream(imageUrls.split("\\R"))
                .map(String::trim)
                .filter(s -> s.startsWith("/uploads/") || s.startsWith("https://"))
                .distinct()
                .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

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
    public Long getPrice() { return price; }
    public void setPrice(Long price) { this.price = price; }
    public Long getOldPrice() { return oldPrice; }
    public void setOldPrice(Long oldPrice) { this.oldPrice = oldPrice; }
    public String getSizes() { return sizes; }
    public void setSizes(String sizes) { this.sizes = sizes; }
    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isTelegramSync() { return telegramSync; }
    public void setTelegramSync(boolean telegramSync) { this.telegramSync = telegramSync; }
    public Long getTelegramPostId() { return telegramPostId; }
    public void setTelegramPostId(Long telegramPostId) { this.telegramPostId = telegramPostId; }
}
