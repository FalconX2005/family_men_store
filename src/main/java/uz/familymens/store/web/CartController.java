package uz.familymens.store.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.familymens.store.model.CustomerOrder;
import uz.familymens.store.security.RateLimiter;
import uz.familymens.store.service.CartService;
import uz.familymens.store.service.OrderService;
import uz.familymens.store.web.dto.CheckoutForm;

import java.time.Duration;
import java.util.Locale;

@Controller
public class CartController {

    /** Bir IP dan bir soatda ko'pi bilan shuncha buyurtma (spam / Telegram to'lib ketishidan himoya) */
    static final int MAX_ORDERS_PER_HOUR = 5;

    private final CartService cartService;
    private final OrderService orderService;
    private final RateLimiter limiter;

    public CartController(CartService cartService, OrderService orderService, RateLimiter limiter) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.limiter = limiter;
    }

    @GetMapping("/cart")
    public String cart(Model model) {
        model.addAttribute("cart", cartService.view());
        if (!model.containsAttribute("checkoutForm")) {
            model.addAttribute("checkoutForm", new CheckoutForm());
        }
        return "cart";
    }

    @PostMapping("/cart/add")
    public String add(@RequestParam long productId,
                      @RequestParam(required = false) String size,
                      @RequestParam(defaultValue = "1") int quantity,
                      RedirectAttributes ra) {
        if (!cartService.add(productId, size, quantity)) {
            ra.addFlashAttribute("error", "pick_size");
            return "redirect:/product/" + productId;
        }
        ra.addFlashAttribute("toast", "added");
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam int index, @RequestParam int quantity) {
        cartService.update(index, quantity);
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam int index) {
        cartService.remove(index);
        return "redirect:/cart";
    }

    @PostMapping("/checkout")
    public String checkout(@Valid @ModelAttribute("checkoutForm") CheckoutForm form,
                           BindingResult result,
                           Locale locale,
                           Model model,
                           HttpServletRequest request,
                           RedirectAttributes ra) {
        CartService.CartView cart = cartService.view();
        if (cart.isEmpty()) {
            return "redirect:/cart";
        }
        if (result.hasErrors()) {
            model.addAttribute("cart", cart);
            return "cart";
        }
        if (!limiter.tryAcquire("order:" + request.getRemoteAddr(), MAX_ORDERS_PER_HOUR, Duration.ofHours(1))) {
            model.addAttribute("cart", cart);
            model.addAttribute("orderLimit", true);
            return "cart";
        }
        CustomerOrder order = orderService.placeOrder(form, "ru".equals(locale.getLanguage()) ? "ru" : "uz");
        cartService.clear();
        ra.addFlashAttribute("orderId", order.getId());
        return "redirect:/order/success";
    }

    @GetMapping("/order/success")
    public String success(Model model) {
        // orderId faqat flash orqali keladi — boshqa birovning buyurtmasini ko'rib bo'lmaydi
        if (!(model.getAttribute("orderId") instanceof Long orderId)) return "redirect:/";
        CustomerOrder order = orderService.find(orderId).orElse(null);
        if (order == null) return "redirect:/";
        model.addAttribute("order", order);
        return "order-success";
    }
}
