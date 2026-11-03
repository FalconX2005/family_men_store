package uz.familymens.store.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uz.familymens.store.config.StoreProperties;
import uz.familymens.store.model.Category;
import uz.familymens.store.service.CartService;
import uz.familymens.store.service.SiteSettingsService;

import java.util.Locale;

/** Barcha sahifalarga umumiy ma'lumotlarni qo'shadi (header, footer uchun). */
@ControllerAdvice(basePackages = "uz.familymens.store.web")
public class GlobalModelAdvice {

    private final StoreProperties store;
    private final CartService cartService;
    private final SiteSettingsService site;

    public GlobalModelAdvice(StoreProperties store, CartService cartService, SiteSettingsService site) {
        this.store = store;
        this.cartService = cartService;
        this.site = site;
    }

    /** Admin paneldan o'zgartirilgan matn va rasmlar: ${site.text('hero.title1', lang)} */
    @ModelAttribute("site")
    public SiteSettingsService site() {
        return site;
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
