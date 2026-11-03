package uz.familymens.store.web;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Shablonlarda: ${@fmt.money(p.price)} -> "$85" */
@Component("fmt")
public class Formatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public String money(Long value) {
        if (value == null) return "";
        return "$" + String.format("%,d", value).replace(',', ' ');
    }

    public String dateTime(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE_TIME);
    }

    public String date(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE);
    }
}
