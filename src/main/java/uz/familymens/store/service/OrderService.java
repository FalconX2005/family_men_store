package uz.familymens.store.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.familymens.store.model.CustomerOrder;
import uz.familymens.store.model.OrderItem;
import uz.familymens.store.model.OrderStatus;
import uz.familymens.store.repository.OrderRepository;
import uz.familymens.store.web.dto.CheckoutForm;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository, CartService cartService, ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.events = events;
    }

    /**
     * Savatchadan buyurtma yaratadi. Telegram xabari tranzaksiya muvaffaqiyatli
     * yakunlangandan keyin yuboriladi (OrderPlacedEvent). Savatchani controller tozalaydi.
     */
    @Transactional
    public CustomerOrder placeOrder(CheckoutForm form, String lang) {
        CartService.CartView cart = cartService.view();
        if (cart.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        CustomerOrder order = new CustomerOrder();
        order.setCustomerName(form.getName().trim());
        order.setPhone(form.getPhone().trim());
        order.setAddress(blankToNull(form.getAddress()));
        order.setComment(blankToNull(form.getComment()));
        order.setLanguage(lang);

        for (CartService.CartLine line : cart.lines()) {
            OrderItem item = new OrderItem();
            item.setProductId(line.product().getId());
            item.setTelegramPostId(line.product().getTelegramPostId());
            item.setBrand(line.product().getBrand());
            item.setProductName(line.product().name(lang));
            item.setSize(line.size());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.product().getPrice());
            order.addItem(item);
        }
        order.setTotal(cart.total());

        CustomerOrder saved = orderRepository.save(order);
        events.publishEvent(new OrderPlacedEvent(saved));
        return saved;
    }

    public Optional<CustomerOrder> find(Long id) {
        return orderRepository.findById(id);
    }

    public List<CustomerOrder> all() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public long countNew() {
        return orderRepository.countByStatus(OrderStatus.NEW);
    }

    @Transactional
    public void updateStatus(Long id, OrderStatus status) {
        orderRepository.findById(id).ifPresent(o -> o.setStatus(status));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
