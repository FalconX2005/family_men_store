package uz.familymens.store.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uz.familymens.store.model.Brands;
import uz.familymens.store.model.Category;
import uz.familymens.store.model.Product;
import uz.familymens.store.repository.ProductRepository;
import uz.familymens.store.web.dto.ProductForm;

import java.util.*;

@Service
public class ProductService {

    public static final int PAGE_SIZE = 24;

    private final ProductRepository repository;
    private final FileStorageService fileStorage;

    public ProductService(ProductRepository repository, FileStorageService fileStorage) {
        this.repository = repository;
        this.fileStorage = fileStorage;
    }

    public Page<Product> catalog(String brand, Category category, int page) {
        return repository.search(brand, category, PageRequest.of(Math.max(0, page), PAGE_SIZE));
    }

    /** Filtrlar uchun: avval Nike, Adidas, Puma (har doim), keyin qolgan brendlar. */
    public List<String> brands() {
        List<String> active = repository.activeBrands();
        List<String> result = new ArrayList<>(Brands.MAIN);
        for (String b : active) if (!result.contains(b)) result.add(b);
        return result;
    }

    public List<Category> categories() {
        Set<Category> active = EnumSet.noneOf(Category.class);
        active.addAll(repository.activeCategories());
        return Arrays.stream(Category.values()).filter(active::contains).toList();
    }

    /** Bosh sahifadagi brend kartalari uchun — har brendning eng so'nggi rasmi. */
    public Map<String, String> brandCovers() {
        Map<String, String> covers = new LinkedHashMap<>();
        for (String b : Brands.MAIN) {
            covers.put(b, repository.findFirstByActiveTrueAndBrandOrderByPublishedAtDesc(b)
                    .map(Product::getImageUrl).orElse(null));
        }
        return covers;
    }

    public long activeCount() {
        return repository.countByActiveTrue();
    }

    public Optional<Product> findActive(Long id) {
        return repository.findByIdAndActiveTrue(id);
    }

    public List<Product> related(Product p) {
        return repository.findTop4ByActiveTrueAndBrandAndIdNotOrderByPublishedAtDesc(p.getBrand(), p.getId());
    }

    // ===== Admin =====

    public Page<Product> all(int page) {
        return repository.findAllByOrderByPublishedAtDescIdDesc(PageRequest.of(Math.max(0, page), 50));
    }

    public Optional<Product> find(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Product save(ProductForm form, MultipartFile image) {
        Product p = form.getId() == null
                ? new Product()
                : repository.findById(form.getId()).orElseThrow(() -> new IllegalArgumentException("Product not found"));

        List<String> before = new ArrayList<>(p.getImages());
        form.applyTo(p);

        if (image != null && !image.isEmpty()) {
            List<String> imgs = new ArrayList<>(p.getImages());
            imgs.add(0, fileStorage.store(image));
            p.setImages(imgs);
        }
        // Ro'yxatdan olib tashlangan lokal rasmlarni diskdan ham o'chiramiz
        before.stream().filter(u -> !p.getImages().contains(u)).forEach(fileStorage::deleteIfLocal);
        return repository.save(p);
    }

    /**
     * Yashirish/ko'rsatish. Telegram mahsulotini admin qo'lda yashirsa, sinxronizatsiya
     * uni qayta ko'rsatib yubormasligi uchun telegramSync o'chiriladi.
     */
    @Transactional
    public void toggleActive(Long id) {
        repository.findById(id).ifPresent(p -> {
            p.setActive(!p.isActive());
            if (!p.isActive() && p.fromTelegram()) p.setTelegramSync(false);
        });
    }

    @Transactional
    public void delete(Long id) {
        repository.findById(id).ifPresent(p -> {
            if (p.fromTelegram()) {
                // Butunlay o'chirsak, keyingi sinxronizatsiyada post qayta import qilinadi.
                // Shuning uchun Telegram mahsulotini yashirib, sinxronizatsiyadan chiqaramiz.
                p.setActive(false);
                p.setTelegramSync(false);
                return;
            }
            p.getImages().forEach(fileStorage::deleteIfLocal);
            repository.delete(p);
        });
    }
}
