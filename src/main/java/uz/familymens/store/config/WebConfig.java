package uz.familymens.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import uz.familymens.store.service.FileStorageService;

import java.time.Duration;
import java.util.Locale;

@Configuration
@EnableAsync
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {

    public static final Locale UZ = Locale.forLanguageTag("uz");

    private final FileStorageService fileStorage;

    public WebConfig(FileStorageService fileStorage) {
        this.fileStorage = fileStorage;
    }

    /** Til cookie'da saqlanadi; standart — o'zbek. */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("fms_lang");
        resolver.setDefaultLocale(UZ);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        return resolver;
    }

    /** ?lang=ru yoki ?lang=uz orqali tilni almashtirish. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor);
    }

    /** Admin yuklagan rasmlar: /uploads/** -> ./uploads papkasi */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(FileStorageService.URL_PREFIX + "**")
                .addResourceLocations(fileStorage.root().toUri().toString())
                .setCachePeriod(60 * 60 * 24 * 30);
    }
}
