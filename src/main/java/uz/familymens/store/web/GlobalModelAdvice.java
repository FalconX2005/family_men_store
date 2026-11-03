package uz.familymens.store.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uz.familymens.store.config.StoreProperties;
import uz.familymens.store.model.Category;
import uz.familymens.store.service.CartService;

import java.util.Locale;

/** Barcha sahifalarga umumiy ma'lumotlarni qo'shadi (header, footer uchun). */
@ControllerAdvice(basePackages = "uz.familymens.store.web")
public class GlobalModelAdvice {

    private final StoreProperties store;
    private final CartService cartService;

    public GlobalModelAdvice(StoreProperties store, CartService cartService) {
        this.store = store;
        this.cartService = cartService;
    }

    @ModelAttribute("store")
    public StoreProperties store() {
        return store;
    }

    @ModelAttribute("lang")
    public String lang(Locale locale) {
        return "ru".equals(locale.getLanguage()) ? "ru" : "uz";
    }

    @ModelAttribute("cartCount")
    public int cartCount() {
        return cartService.count();
    }

    @ModelAttribute("categories")
    public Category[] categories() {
        return Category.values();
    }
}
