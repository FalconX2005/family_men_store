package uz.familymens.store.service;

import org.springframework.stereotype.Service;
import uz.familymens.store.model.Product;
import uz.familymens.store.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final Cart cart;
    private final ProductRepository productRepository;

    public CartService(Cart cart, ProductRepository productRepository) {
        this.cart = cart;
        this.productRepository = productRepository;
    }

    /** @return true agar qo'shildi, false agar mahsulot/o'lcham noto'g'ri */
    public boolean add(long productId, String size, int qty) {
        if (qty < 1) qty = 1;
        Product p = productRepository.findByIdAndActiveTrue(productId).orElse(null);
        if (p == null || !p.acceptsSize(size)) return false;
        cart.add(productId, size.trim(), qty);
        return true;
    }

    public void update(int index, int qty) {
        cart.setQuantity(index, qty);
    }

    public void remove(int index) {
        cart.remove(index);
    }

    public void clear() {
        cart.clear();
    }

    /** Header belgisi uchun son. O'chirilgan/nofaol mahsulotlar avval savatdan olib tashlanadi. */
    public int count() {
        if (cart.isEmpty()) return 0;
        pruneInactive();
        return cart.count();
    }

    /** Savatchani bazadagi joriy narxlar bilan hisoblaydi. O'chirilgan mahsulotlar savatdan olib tashlanadi. */
    public CartView view() {
        Map<Long, Product> products = pruneInactive();
        List<Cart.Entry> entries = cart.entries();

        List<CartLine> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Cart.Entry e = entries.get(i);
            Product p = products.get(e.productId());
            if (p != null) lines.add(new CartLine(i, p, e.size(), e.quantity()));
        }
        long total = lines.stream().mapToLong(CartLine::lineTotal).sum();
        int count = lines.stream().mapToInt(CartLine::quantity).sum();
        return new CartView(lines, total, count);
    }

    /**
     * Bazada yo'q yoki nofaol mahsulotlarni sessiya savatidan o'chiradi.
     * @return savatdagi faol mahsulotlar (id bo'yicha)
     */
    private Map<Long, Product> pruneInactive() {
        if (cart.isEmpty()) return Map.of();
        Map<Long, Product> products = productRepository
                .findAllById(cart.entries().stream().map(Cart.Entry::productId).distinct().toList())
                .stream()
                .filter(Product::isActive)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        cart.retainProducts(products.keySet());
        return products;
    }

    public record CartLine(int index, Product product, String size, int quantity) {
        public long lineTotal() {
            return product.getPrice() * quantity;
        }
    }

    public record CartView(List<CartLine> lines, long total, int count) {
        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }
}
