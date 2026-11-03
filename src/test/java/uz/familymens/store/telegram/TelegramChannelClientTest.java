package uz.familymens.store.telegram;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import uz.familymens.store.model.Brands;
import uz.familymens.store.model.Category;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramChannelClientTest {

    /** t.me/s/familystoreformen sahifasining soddalashtirilgan nusxasi */
    private static final String HTML = """
            <div class="tgme_widget_message js-widget_message" data-post="familystoreformen/21064">
              <div class="tgme_widget_message_grouped_wrap">
                <a class="tgme_widget_message_photo_wrap" style="width:225px;background-image:url('https://cdn4.telesco.pe/file/a.jpg')"></a>
                <a class="tgme_widget_message_photo_wrap" style="width:225px;background-image:url('https://cdn4.telesco.pe/file/b.jpg')"></a>
              </div>
              <div class="tgme_widget_message_text js-message_text"><div class="tgme_widget_message_text js-message_text">Adidas original Amerika <br/>Narxi <b>&#036;85</b>+  <s>&#036;165+</s><br/>Buyurtma uchun:<br/><a href="https://t.me/familystoremen_admin">@familystoremen_admin</a><br/><a href="https://t.me/familystoremen">@familystoremen</a> <br/>Tel: 333 800 100</div></div>
              <time datetime="2026-11-02T11:23:31+00:00"></time>
            </div>
            <div class="tgme_widget_message js-widget_message" data-post="familystoreformen/21082">
              <div class="tgme_widget_message_text">🧥 O'rdak pati bilan kurtka sizga qiziqmi?</div>
            </div>
            <div class="tme_messages_more js-messages_more" data-before="21064"></div>
            """;

    @Test
    void parsesAlbumPostWithPrices() {
        var page = new TelegramChannelClient(null).parse(Jsoup.parse(HTML));

        assertThat(page.minId()).isEqualTo(21064);
        assertThat(page.maxId()).isEqualTo(21082);
        assertThat(page.older()).isEqualTo(21064);

        ChannelPost post = page.posts().get(0);
        assertThat(post.isProduct()).isTrue();
        assertThat(post.price()).isEqualTo(85);
        assertThat(post.oldPrice()).isEqualTo(165);
        assertThat(post.imageUrls()).containsExactly("https://cdn4.telesco.pe/file/a.jpg", "https://cdn4.telesco.pe/file/b.jpg");
        assertThat(post.publishedAt()).isNotNull();
        assertThat(post.caption()).startsWith("Adidas original Amerika\nNarxi $85+");

        // So'rovnoma — rasmsiz va narxsiz, mahsulot emas
        assertThat(page.posts().get(1).isProduct()).isFalse();
    }

    @Test
    void extractsTitleAndStripsContactLines() {
        String caption = "Nike Air Max 90\nCharm teri, original\nNarxi $120+\nBuyurtma uchun:\n@familystoremen_admin\nTel: 333 800 100";
        assertThat(TelegramSyncService.title(caption, "Nike")).isEqualTo("Nike Air Max 90");
        assertThat(TelegramSyncService.cleanDescription(caption)).isEqualTo("Charm teri, original");
        assertThat(Brands.detect(caption)).isEqualTo("Nike");
        assertThat(Category.detect("Puma krossovka original")).isEqualTo(Category.SHOES);
        assertThat(Category.detect("Adidas original Amerika")).isNull();
    }
}
