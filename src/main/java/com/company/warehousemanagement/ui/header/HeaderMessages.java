package com.company.warehousemanagement.ui.header;

import com.vaadin.flow.component.UI;
import io.jmix.core.Messages;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Message-bundle access for the header panels. Texts are always resolved with the UI's
 * CURRENT locale so a language switch (EN/VI) takes effect immediately.
 */
@Component
public class HeaderMessages {

    public static final String GROUP = "com.company.warehousemanagement.ui.header";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Messages messages;
    private final CurrentAuthentication currentAuthentication;

    public HeaderMessages(Messages messages, CurrentAuthentication currentAuthentication) {
        this.messages = messages;
        this.currentAuthentication = currentAuthentication;
    }

    public Locale locale() {
        UI ui = UI.getCurrent();
        if (ui != null && ui.getLocale() != null) {
            return ui.getLocale();
        }
        return currentAuthentication.getLocale();
    }

    public String get(String key) {
        return messages.getMessage(GROUP, key, locale());
    }

    public String format(String key, Object... params) {
        return messages.formatMessage(GROUP, key, locale(), params);
    }

    /** "Just now", "5 min ago", "3 h ago", otherwise dd/MM/yyyy HH:mm in the user's time zone. */
    public String relativeTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        Duration age = Duration.between(instant, Instant.now());
        if (age.isNegative() || age.toMinutes() < 1) {
            return get("time.justNow");
        }
        if (age.toMinutes() < 60) {
            return format("time.minutesAgo", age.toMinutes());
        }
        if (age.toHours() < 24) {
            return format("time.hoursAgo", age.toHours());
        }
        ZoneId zone = currentAuthentication.getTimeZone().toZoneId();
        return DATE_TIME.format(instant.atZone(zone));
    }
}
