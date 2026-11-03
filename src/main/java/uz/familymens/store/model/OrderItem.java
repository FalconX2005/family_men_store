package uz.familymens.store.model;

import jakarta.persistence.*;

/**
 * Buyurtma qatori. Mahsulot ma'lumotlari (nom, narx) buyurtma paytidagi holatda
 * nusxalanadi — keyin mahsulot o'zgarsa yoki o'chirilsa ham buyurtma tarixi saqlanadi.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    private Long productId;

    /** Mahsulot Telegram kanaldan bo'lsa — post raqami (admin tez topishi uchun) */
    private Long telegramPostId;

    @Column(nullable = false, length = 80)
    private String brand;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long unitPrice;

    public long lineTotal() {
        return unitPrice * quantity;
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public void setOrder(CustomerOrder order) { this.order = order; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getTelegramPostId() { return telegramPostId; }
    public void setTelegramPostId(Long telegramPostId) { this.telegramPostId = telegramPostId; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(long unitPrice) { this.unitPrice = unitPrice; }
}
