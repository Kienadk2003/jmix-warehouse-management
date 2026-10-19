package com.company.warehousemanagement.service;

import com.company.warehousemanagement.email.EmailInboxService;
import com.company.warehousemanagement.email.EmailInboxSettings;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides the two header badges from two independent sources:
 * <ul>
 *   <li>website: counted from the database for the current user</li>
 *   <li>email: counted on the IMAP server</li>
 * </ul>
 *
 * <p>The IMAP count is cached and refreshed on a background thread. The UI thread only
 * ever reads the cache, so a slow or unreachable mail server can never freeze a user
 * session (Vaadin holds the session lock while a UI request runs). The mailbox is one
 * shared mailbox, so one cache entry serves all users; permission is checked per call.</p>
 */
@Service
public class UnreadCountService {

    private static final Logger log = LoggerFactory.getLogger(UnreadCountService.class);
    private static final Duration FAILURE_BACKOFF = Duration.ofMinutes(2);

    private final AppNotificationService notificationService;
    private final EmailInboxService emailInboxService;
    private final EmailInboxSettings settings;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "email-unread-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private volatile long cachedCount = -1;
    private volatile Instant checkedAt = Instant.EPOCH;
    private volatile boolean lastRefreshFailed = false;

    public UnreadCountService(AppNotificationService notificationService,
                              EmailInboxService emailInboxService,
                              EmailInboxSettings settings) {
        this.notificationService = notificationService;
        this.emailInboxService = emailInboxService;
        this.settings = settings;
    }

    /** Unread website notifications of the current user. */
    public long getWebsiteUnreadCount() {
        return notificationService.countUnreadForCurrentUser();
    }

    /**
     * Cached unread email count. Empty when the mailbox is not configured, the user may not
     * read it, or no successful count is available yet (the badge is then hidden).
     */
    public OptionalLong getEmailUnreadCount() {
        if (!emailInboxService.isConfigured() || !emailInboxService.canAccess()) {
            return OptionalLong.empty();
        }
        Duration ttl = lastRefreshFailed ? FAILURE_BACKOFF : Duration.ofSeconds(settings.unreadCacheSeconds());
        if (Duration.between(checkedAt, Instant.now()).compareTo(ttl) >= 0
                && refreshing.compareAndSet(false, true)) {
            executor.submit(this::refreshEmailCount);
        }
        long value = cachedCount;
        return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /** Call after the user changes a message's read state so the next poll re-counts. */
    public void invalidateEmailCount() {
        checkedAt = Instant.EPOCH;
    }

    private void refreshEmailCount() {
        try {
            cachedCount = emailInboxService.fetchUnreadCount();
            lastRefreshFailed = false;
        } catch (RuntimeException exception) {
            lastRefreshFailed = true;
            cachedCount = -1;
            log.debug("Could not refresh the unread email count", exception);
        } finally {
            checkedAt = Instant.now();
            refreshing.set(false);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
