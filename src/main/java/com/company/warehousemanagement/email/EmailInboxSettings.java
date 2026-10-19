package com.company.warehousemanagement.email;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * IMAP connection settings. Everything comes from external configuration
 * (environment variables mapped in application.properties); nothing is hard-coded and
 * no credential is ever logged.
 */
@Component
public class EmailInboxSettings {

    private final Environment environment;

    public EmailInboxSettings(Environment environment) {
        this.environment = environment;
    }

    public String host() {
        return text("khoflow.inbox.imap.host");
    }

    public int port() {
        return number("khoflow.inbox.imap.port", 993);
    }

    public boolean ssl() {
        return Boolean.parseBoolean(environment.getProperty("khoflow.inbox.imap.ssl", "true").trim());
    }

    public boolean startTls() {
        return Boolean.parseBoolean(environment.getProperty("khoflow.inbox.imap.starttls", "false").trim());
    }

    public String username() {
        return text("khoflow.inbox.imap.username");
    }

    public String password() {
        // Not trimmed: a password may legitimately contain spaces.
        return environment.getProperty("khoflow.inbox.imap.password", "");
    }

    public String folder() {
        String folder = text("khoflow.inbox.imap.folder");
        return folder.isEmpty() ? "INBOX" : folder;
    }

    public int connectionTimeoutMillis() {
        return number("khoflow.inbox.imap.connection-timeout-ms", 5000);
    }

    public int readTimeoutMillis() {
        return number("khoflow.inbox.imap.read-timeout-ms", 10000);
    }

    public int unreadCacheSeconds() {
        return number("khoflow.inbox.unread-cache-seconds", 60);
    }

    /** The mailbox counts as configured only when host, user and password are all present. */
    public boolean isConfigured() {
        return !host().isEmpty() && !username().isEmpty() && !password().isEmpty();
    }

    private String text(String key) {
        return environment.getProperty(key, "").trim();
    }

    private int number(String key, int fallback) {
        try {
            return Integer.parseInt(environment.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
