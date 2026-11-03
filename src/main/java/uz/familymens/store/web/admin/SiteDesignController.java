package uz.familymens.store.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.familymens.store.config.SecurityConfig;
import uz.familymens.store.config.StoreProperties;
import uz.familymens.store.service.OrderService;
import uz.familymens.store.service.SiteSettingsService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Admin: sayt dizayni — bosh sahifa rasmlari va matnlari. */
@Controller
@RequestMapping("/admin/site")
public class SiteDesignController {

    /** Forma maydonlari prefikslari: t_ — matn, f_ — yangi rasm, r_ — rasmni standartga qaytarish */
    private static final String TEXT = "t_", FILE = "f_", RESET = "r_";

    private final SiteSettingsService settings;
    private final OrderService orderService;
    private final StoreProperties store;

    public SiteDesignController(SiteSettingsService settings, OrderService orderService, StoreProperties store) {
        this.settings = settings;
        this.orderService = orderService;
        this.store = store;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("newOrders", orderService.countNew());
        model.addAttribute("defaultPassword", SecurityConfig.DEFAULT_PASSWORD.equals(store.adminPassword()));
        model.addAttribute("values", settings.all());
        model.addAttribute("texts", SiteSettingsService.TEXTS);
        model.addAttribute("plainValues", SiteSettingsService.VALUES);
        model.addAttribute("images", SiteSettingsService.IMAGES);
        model.addAttribute("langs", SiteSettingsService.LANGS);
        return "admin/site";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params,
                       MultipartHttpServletRequest request,
                       RedirectAttributes ra) {
        Map<String, String> texts = new HashMap<>();
        Set<String> resets = new HashSet<>();
        params.forEach((name, value) -> {
            if (name.startsWith(TEXT)) texts.put(name.substring(TEXT.length()), value);
            else if (name.startsWith(RESET)) resets.add(name.substring(RESET.length()));
        });
        Map<String, MultipartFile> uploads = new HashMap<>();
        request.getFileMap().forEach((name, file) -> {
            if (name.startsWith(FILE)) uploads.put(name.substring(FILE.length()), file);
        });

        try {
            settings.save(texts, uploads, resets);
            ra.addFlashAttribute("message", "Sayt dizayni saqlandi");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/site";
    }
}
