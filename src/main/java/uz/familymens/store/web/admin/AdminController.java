package uz.familymens.store.web.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.familymens.store.model.Category;
import uz.familymens.store.model.OrderStatus;
import uz.familymens.store.model.ProductTag;
import uz.familymens.store.service.OrderService;
import uz.familymens.store.service.ProductService;
import uz.familymens.store.telegram.TelegramBotListener;
import uz.familymens.store.telegram.TelegramSyncService;
import uz.familymens.store.web.dto.ProductForm;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final TelegramSyncService syncService;
    private final TelegramBotListener botListener;
    private final uz.familymens.store.config.StoreProperties store;

    public AdminController(ProductService productService, OrderService orderService,
                           TelegramSyncService syncService, TelegramBotListener botListener,
                           uz.familymens.store.config.StoreProperties store) {
        this.store = store;
        this.productService = productService;
        this.orderService = orderService;
        this.syncService = syncService;
        this.botListener = botListener;
    }

    @ModelAttribute
    void common(Model model) {
        model.addAttribute("newOrders", orderService.countNew());
        model.addAttribute("allCategories", Category.values());
        model.addAttribute("allTags", ProductTag.values());
        model.addAttribute("allStatuses", OrderStatus.values());
        model.addAttribute("defaultPassword",
                uz.familymens.store.config.SecurityConfig.DEFAULT_PASSWORD.equals(store.adminPassword()));
    }

    @GetMapping("/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/orders";
    }

    // ===================== Buyurtmalar =====================

    @GetMapping("/orders")
    public String orders(Model model) {
        model.addAttribute("orders", orderService.all());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String order(@PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "admin/order";
    }

    @PostMapping("/orders/{id}/status")
    public String orderStatus(@PathVariable Long id, @RequestParam OrderStatus status, RedirectAttributes ra) {
        orderService.updateStatus(id, status);
        ra.addFlashAttribute("message", "Holat yangilandi");
        return "redirect:/admin/orders/" + id;
    }

    // ===================== Mahsulotlar =====================

    @GetMapping("/products")
    public String products(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("products", productService.all(page));
        model.addAttribute("sync", syncService.status());
        model.addAttribute("instantListening", botListener.listening());
        model.addAttribute("botStatus", botListener.listening() ? botListener.botStatusInChannel() : null);
        return "admin/products";
    }

    @GetMapping("/products/new")
    public String newProduct(Model model) {
        model.addAttribute("form", new ProductForm());
        return "admin/product-form";
    }

    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model) {
        model.addAttribute("form", productService.find(id)
                .map(ProductForm::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "admin/product-form";
    }

    @PostMapping("/products")
    public String saveProduct(@Valid @ModelAttribute("form") ProductForm form,
                              BindingResult result,
                              @RequestParam(value = "image", required = false) MultipartFile image,
                              RedirectAttributes ra) {
        boolean hasImage = (image != null && !image.isEmpty()) || !form.imageList().isEmpty();
        if (!hasImage) result.reject("image", "Kamida bitta rasm kerak");
        if (result.hasErrors()) {
            return "admin/product-form";
        }
        try {
            productService.save(form, image);
        } catch (IllegalArgumentException e) {
            result.reject("image", e.getMessage());
            return "admin/product-form";
        }
        ra.addFlashAttribute("message", "Mahsulot saqlandi");
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        productService.toggleActive(id);
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        productService.delete(id);
        ra.addFlashAttribute("message", "Mahsulot o'chirildi");
        return "redirect:/admin/products";
    }

    // ===================== Telegram sinxronizatsiya =====================

    @PostMapping("/sync")
    public String sync(RedirectAttributes ra) {
        ra.addFlashAttribute("message", "Telegram: " + syncService.syncLatest().message());
        return "redirect:/admin/products";
    }

    /** Sanadan keyingi barcha postlarni fonda yuklaydi (uzoq davom etishi mumkin). */
    @PostMapping("/sync/since")
    public String syncSince(@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                            RedirectAttributes ra) {
        if (syncService.isRunning()) {
            ra.addFlashAttribute("message", "Sinxronizatsiya hozir ishlayapti, birozdan so'ng qayta urinib ko'ring");
        } else {
            java.util.concurrent.CompletableFuture.runAsync(() -> syncService.loadSince(date));
            ra.addFlashAttribute("message", date + " dan keyingi postlar fonda yuklanmoqda. Sahifani yangilab, holatini kuzating.");
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/sync/older")
    public String syncOlder(@RequestParam(defaultValue = "5") int pages, RedirectAttributes ra) {
        int p = Math.max(1, Math.min(pages, 50));
        ra.addFlashAttribute("message", "Telegram (eski postlar): " + syncService.loadOlder(p).message());
        return "redirect:/admin/products";
    }
}
