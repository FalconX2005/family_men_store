package uz.familymens.store.model;

import java.util.List;
import java.util.Locale;

/** Do'kon ishlaydigan brendlar va post matnidan brendni aniqlash. */
public final class Brands {

    /** Asosiy brendlar — bosh sahifada alohida ko'rsatiladi. */
    public static final List<String> MAIN = List.of("Nike", "Adidas", "Puma");

    private static final List<String> KNOWN = List.of(
            "Nike", "Adidas", "Puma", "Jordan", "New Balance", "Reebok", "Under Armour", "The North Face",
            "Columbia", "Asics", "Converse", "Vans", "Fila", "Skechers", "Champion", "Tommy Hilfiger",
            "Calvin Klein", "Ralph Lauren", "Lacoste", "Levi's", "Hugo Boss", "Guess", "Michael Kors",
            "Timberland", "Carhartt", "Hollister", "Abercrombie", "Gap", "Zara", "Mango", "LC Waikiki", "Defacto"
    );

    public static final String FALLBACK = "Original";

    private Brands() {
    }

    /** Matnda eng birinchi uchragan brend (masalan "Nike Air Jordan" -> Nike). */
    public static String detect(String text) {
        if (text == null) return FALLBACK;
        String t = text.toLowerCase(Locale.ROOT);
        String best = null;
        int bestPos = Integer.MAX_VALUE;
        for (String b : KNOWN) {
            int pos = t.indexOf(b.toLowerCase(Locale.ROOT));
            if (pos >= 0 && pos < bestPos) {
                bestPos = pos;
                best = b;
            }
        }
        return best != null ? best : FALLBACK;
    }
}
