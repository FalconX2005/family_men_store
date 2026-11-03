package uz.familymens.store.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import uz.familymens.store.config.StoreProperties;
import uz.familymens.store.model.CustomerOrder;
import uz.familymens.store.model.OrderItem;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Yangi buyurtma haqida Telegram bot orqali admin'ga xabar yuboradi.
 * store.telegram-bot-token va store.telegram-chat-id bo'sh bo'lsa — hech narsa qilmaydi.
 */
@Service
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final StoreProperties props;
    private final RestClient client;

    public TelegramNotifier(StoreProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.client = RestClient.builder()
                .baseUrl("https://api.telegram.org")
                .requestFactory(factory)
                .build();
    }

    /** Buyurtma bazaga yozilgandan (commit) keyin, alohida oqimda ishlaydi. */
    @Async
    @TransactionalEventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (!props.telegramBotEnabled()) return;
        CustomerOrder order = event.order();
        try {
            client.post()
                    .uri("/bot{token}/sendMessage", props.telegramBotToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", props.telegramChatId(),
                            "text", format(order),
                            "parse_mode", "HTML",
                            "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Buyurtma bazada saqlangan — xabar yuborilmasa ham jarayon to'xtamaydi
            log.error("Telegram notification failed for order #{}", order.getId(), e);
        }
    }

    String format(CustomerOrder o) {
        StringBuilder sb = new StringBuilder();
        sb.append("🛍 <b>Saytdan yangi buyurtma #").append(o.getId()).append("</b>\n");
        sb.append("🕒 ").append(o.getCreatedAt().format(DATE)).append("\n\n");
        int i = 1;
        for (OrderItem it : o.getItems()) {
            sb.append(i++).append(". <b>").append(esc(it.getBrand())).append("</b> — ")
              .append(esc(it.getProductName()))
              .append("\n    O'lcham: ").append(esc(it.getSize()))
              .append(" | ").append(it.getQuantity()).append(" × ").append(money(it.getUnitPrice()));
            if (it.getTelegramPostId() != null) {
                sb.append("\n    🔗 ").append(props.postUrl(it.getTelegramPostId()));
            }
            sb.append("\n");
        }
        sb.append("\n💰 <b>Jami: ").append(money(o.getTotal())).append("</b> + kargo\n");
        sb.append("💳 Oldindan to'lov (").append(props.prepaymentPercent()).append("%): <b>")
          .append(money(props.prepayment(o.getTotal()))).append("</b>\n\n");
        sb.append("👤 ").append(esc(o.getCustomerName())).append("\n");
        sb.append("📞 ").append(esc(o.getPhone())).append("\n");
        if (o.getAddress() != null) sb.append("📍 ").append(esc(o.getAddress())).append("\n");
        if (o.getComment() != null) sb.append("💬 ").append(esc(o.getComment())).append("\n");
        return sb.toString();
    }

    private static String money(long v) {
        return "$" + String.format("%,d", v).replace(',', ' ');
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
