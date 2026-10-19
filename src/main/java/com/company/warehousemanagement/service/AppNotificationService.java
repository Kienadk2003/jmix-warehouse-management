package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.AppNotification;
import com.company.warehousemanagement.entity.NotificationType;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manages internal website notifications.
 *
 * <p>Isolation between users is enforced HERE, on the server: every read and every
 * update filters by the username of the authenticated user (taken from
 * {@link CurrentAuthentication}), never by a value supplied by the UI. A notification
 * id that belongs to someone else behaves exactly like an id that does not exist.</p>
 *
 * <p>The unconstrained data manager is used on purpose: ordinary roles are not given
 * entity permissions on AppNotification, so the only way to touch the table is
 * through this service and its per-user filter.</p>
 */
@Service
public class AppNotificationService {

    public static final int DEFAULT_PAGE_SIZE = 15;
    public static final int MAX_PAGE_SIZE = 50;
    private static final int MARK_ALL_BATCH = 200;
    private static final int MARK_ALL_MAX_BATCHES = 25;

    private final UnconstrainedDataManager dataManager;
    private final CurrentAuthentication currentAuthentication;

    public AppNotificationService(UnconstrainedDataManager dataManager,
                                  CurrentAuthentication currentAuthentication) {
        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
    }

    /** Read-only view of a notification for the UI (the UI never touches entities). */
    public record NotificationItem(UUID id,
                                   NotificationType type,
                                   String subjectType,
                                   String subjectNo,
                                   String actorName,
                                   String detail,
                                   String targetViewId,
                                   String targetRoute,
                                   OffsetDateTime createdAt,
                                   boolean read) {
    }

    public record NotificationPage(List<NotificationItem> items, boolean hasMore) {
    }

    /** One page of the current user's notifications, newest first. */
    public NotificationPage listForCurrentUser(int offset, int limit) {
        String username = currentUsername();
        int size = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        // One query, no references -> no N+1. size + 1 rows tell us if another page exists.
        List<AppNotification> rows = dataManager.load(AppNotification.class)
                .query("select e from AppNotification e where e.recipientUsername = :username "
                        + "order by e.createdAt desc, e.id desc")
                .parameter("username", username)
                .firstResult(Math.max(offset, 0))
                .maxResults(size + 1)
                .list();

        boolean hasMore = rows.size() > size;
        List<NotificationItem> items = new ArrayList<>(Math.min(rows.size(), size));
        for (int i = 0; i < rows.size() && i < size; i++) {
            items.add(toItem(rows.get(i)));
        }
        return new NotificationPage(items, hasMore);
    }

    public long countUnreadForCurrentUser() {
        Long count = dataManager.loadValue(
                        "select count(e) from AppNotification e "
                                + "where e.recipientUsername = :username and e.readFlag = false",
                        Long.class)
                .parameter("username", currentUsername())
                .one();
        return count == null ? 0L : count;
    }

    /**
     * Marks one notification of the current user as read.
     *
     * @return false if the notification does not exist OR belongs to another user
     */
    @Transactional
    public boolean markRead(UUID notificationId) {
        if (notificationId == null) {
            return false;
        }
        AppNotification notification = dataManager.load(AppNotification.class)
                .query("select e from AppNotification e "
                        + "where e.id = :id and e.recipientUsername = :username")
                .parameter("id", notificationId)
                .parameter("username", currentUsername())
                .optional()
                .orElse(null);
        if (notification == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(notification.getReadFlag())) {
            notification.setReadFlag(true);
            notification.setReadAt(OffsetDateTime.now());
            dataManager.save(notification);
        }
        return true;
    }

    /** Marks every unread notification of the current user as read. */
    @Transactional
    public int markAllReadForCurrentUser() {
        String username = currentUsername();
        OffsetDateTime now = OffsetDateTime.now();
        int total = 0;
        for (int i = 0; i < MARK_ALL_MAX_BATCHES; i++) {
            List<AppNotification> batch = dataManager.load(AppNotification.class)
                    .query("select e from AppNotification e "
                            + "where e.recipientUsername = :username and e.readFlag = false")
                    .parameter("username", username)
                    .maxResults(MARK_ALL_BATCH)
                    .list();
            if (batch.isEmpty()) {
                break;
            }
            for (AppNotification notification : batch) {
                notification.setReadFlag(true);
                notification.setReadAt(now);
            }
            dataManager.saveAll(batch);
            total += batch.size();
        }
        return total;
    }

    /**
     * Creates one notification per recipient. Runs in its own transaction because it is
     * called after the business transaction has already committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int createForUsers(Collection<String> recipients,
                              NotificationType type,
                              String subjectType,
                              String subjectNo,
                              String actorName,
                              String detail,
                              String targetViewId,
                              String targetRoute) {
        Set<String> distinct = new LinkedHashSet<>();
        if (recipients != null) {
            for (String recipient : recipients) {
                if (recipient != null && !recipient.isBlank()) {
                    distinct.add(recipient.trim());
                }
            }
        }
        if (distinct.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<AppNotification> created = new ArrayList<>(distinct.size());
        for (String recipient : distinct) {
            AppNotification notification = dataManager.create(AppNotification.class);
            notification.setRecipientUsername(recipient);
            notification.setType(type);
            notification.setSubjectType(subjectType);
            notification.setSubjectNo(truncate(subjectNo, 50));
            notification.setActorName(truncate(actorName, 255));
            notification.setDetail(truncate(detail, 500));
            notification.setTargetViewId(targetViewId);
            notification.setTargetRoute(targetRoute);
            notification.setCreatedAt(now);
            notification.setReadFlag(false);
            created.add(notification);
        }
        dataManager.saveAll(created);
        return created.size();
    }

    private NotificationItem toItem(AppNotification n) {
        return new NotificationItem(
                n.getId(), n.getType(), n.getSubjectType(), n.getSubjectNo(), n.getActorName(),
                n.getDetail(), n.getTargetViewId(), n.getTargetRoute(), n.getCreatedAt(),
                Boolean.TRUE.equals(n.getReadFlag()));
    }

    private String currentUsername() {
        return currentAuthentication.getUser().getUsername();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
