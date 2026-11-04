package uz.familymens.store.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.familymens.store.model.Category;
import uz.familymens.store.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where p.active = true
              and (:brand is null or p.brand = :brand)
              and (:category is null or p.category = :category)
            order by p.publishedAt desc, p.id desc
            """)
    Page<Product> search(@Param("brand") String brand, @Param("category") Category category, Pageable pageable);

    @Query("select distinct p.brand from Product p where p.active = true order by p.brand")
    List<String> activeBrands();

    @Query("select distinct p.category from Product p where p.active = true and p.category is not null")
    List<Category> activeCategories();

    Optional<Product> findFirstByActiveTrueAndBrandOrderByPublishedAtDesc(String brand);

    long countByActiveTrue();

    Page<Product> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

    Optional<Product> findByIdAndActiveTrue(Long id);

    List<Product> findTop4ByActiveTrueAndBrandAndIdNotOrderByPublishedAtDesc(String brand, Long id);

    // ===== Telegram sinxronizatsiya =====

    Optional<Product> findByTelegramPostId(Long telegramPostId);

    List<Product> findByTelegramPostIdBetween(Long from, Long to);

    @Query("select min(p.telegramPostId) from Product p where p.telegramPostId is not null")
    Long minTelegramPostId();

    long countByTelegramPostIdIsNotNull();
}
