package uz.familymens.store.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "store")
public record StoreProperties(
        String name,
        String telegramChannel,
        String telegramAdmin,
        String telegramManager,
        String instagramUsername,
        String phone,
        @DefaultValue("20") int prepaymentPercent,
        @DefaultValue("7–15") String deliveryDays,
        String uploadDir,
        String telegramBotToken,
        String telegramChatId,
        String adminUsername,
        String adminPassword,
        @DefaultValue Sync sync
) {
    public record Sync(
            @DefaultValue("true") boolean enabled,
            /* Bot kanalda admin bo'lsa — post qo'yilishi bilan darhol yangilash */
            @DefaultValue("true") boolean instant,
            @DefaultValue("PT10M") Duration interval,
            @DefaultValue("5") int initialPages,
            @DefaultValue("5") int maxPagesPerRun
    ) {
    }

    public String phoneHref() {
        return "tel:" + phone.replaceAll("\\s", "");
    }

    public String channelUrl() {
        return "https://t.me/" + telegramChannel;
    }

    public String postUrl(Long postId) {
        return channelUrl() + "/" + postId;
    }

    public String adminUrl() {
        return "https://t.me/" + telegramAdmin;
    }

    public String managerUrl() {
        return "https://t.me/" + telegramManager;
    }

    public String instagramUrl() {
        return "https://www.instagram.com/" + instagramUsername + "/";
    }

    /** Oldindan to'lov summasi (yuqoriga yaxlitlangan dollar). */
    public long prepayment(long total) {
        return (total * prepaymentPercent + 99) / 100;
    }

    public boolean adminPasswordSet() {
        return adminPassword != null && !adminPassword.isBlank();
    }

    public boolean telegramBotEnabled() {
        return telegramBotToken != null && !telegramBotToken.isBlank()
                && telegramChatId != null && !telegramChatId.isBlank();
    }
}
