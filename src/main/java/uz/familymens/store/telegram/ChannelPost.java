package uz.familymens.store.telegram;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kanal sahifasidan o'qilgan bitta post.
 *
 * @param id        post raqami (t.me/kanal/{id})
 * @param caption   post matni (oddiy matn, qatorlar \n bilan)
 * @param price     joriy narx ($), topilmasa null
 * @param oldPrice  chizilgan narx ($), topilmasa null
 * @param imageUrls albomdagi rasmlar (Telegram CDN)
 */
public record ChannelPost(
        long id,
        LocalDateTime publishedAt,
        String caption,
        Long price,
        Long oldPrice,
        List<String> imageUrls
) {
    public boolean isProduct() {
        return price != null && !imageUrls.isEmpty();
    }
}
