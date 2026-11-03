package uz.familymens.store.web;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import uz.familymens.store.model.Brands;
import uz.familymens.store.model.Category;
import uz.familymens.store.model.Product;
import uz.familymens.store.service.ProductService;

import java.util.List;

@Controller
public class ShopController {

    private final ProductService productService;

    public ShopController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String brand,
                       @RequestParam(required = false) String category,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        List<String> brands = productService.brands();
        String selectedBrand = brand != null && (brands.contains(brand) || Brands.MAIN.contains(brand)) ? brand : null;
        Category selectedCategory = Category.fromKey(category);

        Page<Product> products = productService.catalog(selectedBrand, selectedCategory, page);
        model.addAttribute("products", products);
        model.addAttribute("brands", brands);
        model.addAttribute("shopCategories", productService.categories());
        model.addAttribute("selectedBrand", selectedBrand);
        model.addAttribute("selectedCategory", selectedCategory == null ? null : selectedCategory.key());
        model.addAttribute("brandCovers", productService.brandCovers());
        model.addAttribute("totalProducts", productService.activeCount());
        model.addAttribute("pageBase", UriComponentsBuilder.fromPath("/")
                .queryParamIfPresent("brand", java.util.Optional.ofNullable(selectedBrand))
                .queryParamIfPresent("category", java.util.Optional.ofNullable(selectedCategory).map(Category::key))
                .queryParam("page", "")
                .encode().build().toUriString());
        return "index";
    }

    @GetMapping("/product/{id}")
    public String product(@PathVariable Long id, Model model) {
        Product product = productService.findActive(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("product", product);
        model.addAttribute("related", productService.related(product));
        return "product";
    }
}
