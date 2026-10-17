package com.company.warehousemanagement.ui.header;

import com.company.warehousemanagement.entity.NotificationType;
import com.company.warehousemanagement.service.AppNotificationService;
import com.company.warehousemanagement.service.AppNotificationService.NotificationItem;
import com.company.warehousemanagement.service.AppNotificationService.NotificationPage;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;

import java.util.function.Predicate;

/**
 * Panel behind the bell icon: the current user's persistent website notifications.
 * All data comes from {@link AppNotificationService}; the UI never queries the database.
 */
public class NotificationPanel extends Div {

    private static final int PAGE_SIZE = AppNotificationService.DEFAULT_PAGE_SIZE;

    private final AppNotificationService service;
    private final HeaderMessages msg;
    private final Predicate<String> viewAccessChecker;
    private final Runnable closePanel;
    private final Runnable onUnreadChanged;

    private final Span title = new Span();
    private final Span unreadLabel = new Span();
    private final Button markAllButton = new Button();
    private final Div list = new Div();
    private final Div footer = new Div();

    private int loaded;

    public NotificationPanel(AppNotificationService service,
                             HeaderMessages msg,
                             Predicate<String> viewAccessChecker,
                             Runnable closePanel,
                             Runnable onUnreadChanged) {
        this.service = service;
        this.msg = msg;
        this.viewAccessChecker = viewAccessChecker;
        this.closePanel = closePanel;
        this.onUnreadChanged = onUnreadChanged;

        addClassNames("khoflow-popover-content", "khoflow-panel");

        title.addClassName("khoflow-panel-title");
        unreadLabel.addClassName("khoflow-panel-subtitle");
        Div heading = new Div(title, unreadLabel);
        heading.addClassName("khoflow-panel-heading");

        markAllButton.getElement().setAttribute("theme", "tertiary small");
        markAllButton.addClassName("khoflow-panel-action");
        markAllButton.addClickListener(event -> markAllRead());

        Div header = new Div(heading, markAllButton);
        header.addClassName("khoflow-panel-header");

        list.addClassName("khoflow-panel-list");
        footer.addClassName("khoflow-panel-footer");

        add(header, list, footer);
        applyTexts();
    }

    /** Refreshes the static labels (called on language change). */
    public void applyTexts() {
        title.setText(msg.get("notifications.title"));
        markAllButton.setText(msg.get("notifications.markAllRead"));
        markAllButton.getElement().setAttribute("aria-label", msg.get("notifications.markAllRead"));
    }

    /** Reloads the first page. Called every time the panel is opened. */
    public void reload() {
        applyTexts();
        loaded = 0;
        list.removeAll();
        footer.removeAll();
        showState(msg.get("common.loading"), "khoflow-panel-state");
        loadNextPage(true);
    }

    private void loadNextPage(boolean replace) {
        try {
            NotificationPage page = service.listForCurrentUser(loaded, PAGE_SIZE);
            if (replace) {
                list.removeAll();
            }
            footer.removeAll();
            for (NotificationItem item : page.items()) {
                list.add(createRow(item));
            }
            loaded += page.items().size();

            if (loaded == 0) {
                showState(msg.get("notifications.empty"), "khoflow-panel-state");
            } else if (page.hasMore()) {
                Button more = new Button(msg.get("common.loadMore"), event -> loadNextPage(false));
                more.getElement().setAttribute("theme", "tertiary small");
                footer.add(more);
            }
            updateUnreadHeader();
        } catch (RuntimeException exception) {
            showError();
        }
    }

    private void showError() {
        list.removeAll();
        footer.removeAll();
        Div error = new Div();
        error.addClassNames("khoflow-panel-state", "khoflow-panel-state-error");
        error.add(new Span(msg.get("common.error")));
        Button retry = new Button(msg.get("common.retry"), event -> reload());
        retry.getElement().setAttribute("theme", "small");
        error.add(retry);
        list.add(error);
    }

    private void showState(String text, String className) {
        list.removeAll();
        Div state = new Div(new Span(text));
        state.addClassName(className);
        list.add(state);
    }

    private Div createRow(NotificationItem item) {
        Div row = new Div();
        row.addClassName("khoflow-notice");
        row.getElement().setAttribute("role", "button");
        row.getElement().setAttribute("tabindex", "0");
        if (!item.read()) {
            row.addClassName("khoflow-notice-unread");
        }

        Div iconBox = new Div(iconFor(item.type()));
        iconBox.addClassNames("khoflow-notice-icon", iconModifier(item.type()));

        Span titleSpan = new Span(titleOf(item));
        titleSpan.addClassName("khoflow-notice-title");

        Span body = new Span(bodyOf(item));
        body.addClassName("khoflow-notice-body");

        Span time = new Span(msg.relativeTime(item.createdAt() == null ? null : item.createdAt().toInstant()));
        time.addClassName("khoflow-notice-time");

        Span state = new Span(item.read() ? msg.get("common.read") : msg.get("common.unread"));
        state.addClassName("khoflow-visually-hidden");

        Div text = new Div(titleSpan, body, time, state);
        text.addClassName("khoflow-notice-text");

        Span dot = new Span();
        dot.addClassName("khoflow-unread-dot");
        dot.setVisible(!item.read());

        row.add(iconBox, text, dot);
        row.getElement().setAttribute("aria-label", titleOf(item));

        row.addClickListener(event -> open(item));
        // Keyboard: Enter / Space behave like a click.
        row.getElement().addEventListener("keydown", event -> open(item))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        return row;
    }

    private void open(NotificationItem item) {
        try {
            if (!item.read()) {
                service.markRead(item.id());
                onUnreadChanged.run();
            }
        } catch (RuntimeException ignored) {
            // The notification can still be followed even if marking it failed.
        }

        String route = item.targetRoute();
        if (route == null || route.isBlank()) {
            reload();
            return;
        }
        if (item.targetViewId() != null && !viewAccessChecker.test(item.targetViewId())) {
            Notification.show(msg.get("notifications.noAccess"), 4000, Notification.Position.MIDDLE);
            reload();
            return;
        }
        closePanel.run();
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.navigate(route);
        }
    }

    private void markAllRead() {
        try {
            service.markAllReadForCurrentUser();
            onUnreadChanged.run();
            reload();
        } catch (RuntimeException exception) {
            showError();
        }
    }

    private void updateUnreadHeader() {
        long unread = service.countUnreadForCurrentUser();
        unreadLabel.setText(unread > 0 ? msg.format("notifications.unreadCount", unread) : "");
        unreadLabel.setVisible(unread > 0);
        markAllButton.setEnabled(unread > 0);
    }

    // ---- rendering helpers -------------------------------------------------------------

    private String titleOf(NotificationItem item) {
        if (item.type() == null) {
            return "";
        }
        String title = msg.format("notif." + item.type().getId() + ".title", docKind(item));
        // Some templates start with the document kind ("{0} approved"): capitalise the sentence.
        return title.isEmpty() ? title : Character.toUpperCase(title.charAt(0)) + title.substring(1);
    }

    private String bodyOf(NotificationItem item) {
        if (item.type() == null) {
            return "";
        }
        String body = msg.format("notif." + item.type().getId() + ".body",
                docKind(item), nullToDash(item.subjectNo()), nullToDash(item.actorName()));
        if (item.detail() != null && !item.detail().isBlank()) {
            body += " " + msg.format("notif.reason", item.detail());
        }
        return body;
    }

    private String docKind(NotificationItem item) {
        String kind = item.subjectType() == null ? "STOCKTAKE" : item.subjectType();
        return msg.get("docKind." + kind);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static Icon iconFor(NotificationType type) {
        if (type == null) {
            return new Icon(VaadinIcon.BELL_O);
        }
        return switch (type) {
            case TRANSACTION_SUBMITTED, STOCKTAKE_SUBMITTED -> new Icon(VaadinIcon.CLOCK);
            case TRANSACTION_APPROVED, STOCKTAKE_APPROVED -> new Icon(VaadinIcon.CHECK_CIRCLE_O);
            case TRANSACTION_REJECTED, STOCKTAKE_REJECTED -> new Icon(VaadinIcon.CLOSE_CIRCLE_O);
        };
    }

    private static String iconModifier(NotificationType type) {
        if (type == null) {
            return "khoflow-notice-icon-info";
        }
        return switch (type) {
            case TRANSACTION_SUBMITTED, STOCKTAKE_SUBMITTED -> "khoflow-notice-icon-pending";
            case TRANSACTION_APPROVED, STOCKTAKE_APPROVED -> "khoflow-notice-icon-ok";
            case TRANSACTION_REJECTED, STOCKTAKE_REJECTED -> "khoflow-notice-icon-bad";
        };
    }
}
