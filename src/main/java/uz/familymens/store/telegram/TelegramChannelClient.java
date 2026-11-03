package uz.familymens.store.telegram;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import uz.familymens.store.config.StoreProperties;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ochiq Telegram kanalining veb-ko'rinishini (https://t.me/s/kanal) o'qiydi.
 * Bot yoki token kerak emas — kanal ommaviy bo'lishi kifoya.
 */
@Component
public class TelegramChannelClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final Pattern BG_URL = Pattern.compile("background-image:url\\('([^']+)'\\)");
    private static final Pattern DOLLAR = Pattern.compile("\\$\\s?(\\d{1,6})(?:[.,]\\d{1,2})?");
    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");

    private final StoreProperties props;

    public TelegramChannelClient(StoreProperties props) {
        this.props = props;
    }

    /**
     * @param before null — eng yangi postlar; aks holda shu raqamdan oldingi postlar
     */
    public ChannelPage fetch(Long before) throws IOException {
        String url = "https://t.me/s/" + props.telegramChannel() + (before != null ? "?before=" + before : "");
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "ru,uz;q=0.9,en;q=0.8")
                .timeout(20_000)
                .get();
        return parse(doc);
    }

    ChannelPage parse(Document doc) {
        List<ChannelPost> posts = new ArrayList<>();
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (Element msg : doc.select(".tgme_widget_message[data-post]")) {
            String dataPost = msg.attr("data-post");
            long id;
            try {
                id = Long.parseLong(dataPost.substring(dataPost.lastIndexOf('/') + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            min = Math.min(min, id);
            max = Math.max(max, id);

            Element textEl = msg.selectFirst(".tgme_widget_message_text");
            String caption = textEl == null ? "" : toPlainText(textEl);

            Long price = null;
            Long oldPrice = null;
            if (textEl != null) {
                // Kanal formati: <b>$85</b>+  <s>$165+</s>  — qalin = joriy narx, chizilgan = asl narx
                for (Element s : textEl.select("s, del, strike")) {
                    Long v = firstDollar(s.text());
                    if (v != null) { oldPrice = v; break; }
                }
                for (Element b : textEl.select("b, strong")) {
                    Long v = firstDollar(b.text());
                    if (v != null) { price = v; break; }
                }
                if (price == null) {
                    // Zaxira: matndagi birinchi $ summa
                    Matcher m = DOLLAR.matcher(caption);
                    List<Long> all = new ArrayList<>();
                    while (m.find()) all.add(Long.parseLong(m.group(1)));
                    if (!all.isEmpty()) {
                        price = all.get(0);
                        if (oldPrice == null && all.size() > 1 && all.get(1) > price) oldPrice = all.get(1);
                    }
                }
            }
            if (oldPrice != null && price != null && oldPrice <= price) oldPrice = null;

            List<String> images = new ArrayList<>();
            for (Element photo : msg.select(".tgme_widget_message_photo_wrap")) {
                Matcher m = BG_URL.matcher(photo.attr("style"));
                if (m.find()) images.add(m.group(1));
            }

            LocalDateTime publishedAt = null;
            Element time = msg.selectFirst("time[datetime]");
            if (time != null) {
                try {
                    publishedAt = OffsetDateTime.parse(time.attr("datetime"))
                            .atZoneSameInstant(TASHKENT).toLocalDateTime();
                } catch (Exception ignored) {
                    // sana bo'lmasa — hozirgi vaqt ishlatiladi
                }
            }

            posts.add(new ChannelPost(id, publishedAt, caption.strip(), price, oldPrice, List.copyOf(images)));
        }

        Long older = null;
        Element more = doc.selectFirst(".tme_messages_more[data-before]");
        if (more != null) {
            try { older = Long.parseLong(more.attr("data-before")); } catch (NumberFormatException ignored) { }
        }
        if (posts.isEmpty()) return new ChannelPage(List.of(), null, null, null);
        return new ChannelPage(posts, min, max, older);
    }

    private static Long firstDollar(String s) {
        Matcher m = DOLLAR.matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    /** HTML matnni <br> larni saqlagan holda oddiy matnga aylantiradi. */
    private static String toPlainText(Element el) {
        StringBuilder sb = new StringBuilder();
        appendText(el, sb);
        return sb.toString().replaceAll("[ \\t\\u00A0]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n");
    }

    private static void appendText(Node node, StringBuilder sb) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode t) {
                sb.append(t.text());
            } else if (child instanceof Element e) {
                if (e.normalName().equals("br")) sb.append('\n');
                else appendText(e, sb);
            }
        }
    }

    /**
     * @param minId eng kichik post raqami shu sahifada
     * @param maxId eng katta post raqami shu sahifada
     * @param older keyingi (eskiroq) sahifa uchun "before" qiymati
     */
    public record ChannelPage(List<ChannelPost> posts, Long minId, Long maxId, Long older) {
        public boolean isEmpty() {
            return posts.isEmpty();
        }
    }
}
