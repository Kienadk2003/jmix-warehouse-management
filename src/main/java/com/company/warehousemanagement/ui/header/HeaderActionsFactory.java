package com.company.warehousemanagement.ui.header;

import io.jmix.core.AccessManager;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import com.company.warehousemanagement.email.EmailInboxService;
import com.company.warehousemanagement.service.AppNotificationService;
import com.company.warehousemanagement.service.UnreadCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the header actions and inserts them into the EXISTING header, immediately before
 * the given anchor (the authenticated user menu). No header is created or replaced.
 */
@Component
public class HeaderActionsFactory {

    private static final Logger log = LoggerFactory.getLogger(HeaderActionsFactory.class);

    private final AppNotificationService notificationService;
    private final EmailInboxService emailInboxService;
    private final UnreadCountService unreadCountService;
    private final HeaderMessages messages;
    private final AccessManager accessManager;

    public HeaderActionsFactory(AppNotificationService notificationService,
                                EmailInboxService emailInboxService,
                                UnreadCountService unreadCountService,
                                HeaderMessages messages,
                                AccessManager accessManager) {
        this.notificationService = notificationService;
        this.emailInboxService = emailInboxService;
        this.unreadCountService = unreadCountService;
        this.messages = messages;
        this.accessManager = accessManager;
    }

    /** @return the inserted bar, or null if the anchor is not inside a header. */
    public HeaderActionsBar install(com.vaadin.flow.component.Component anchorBeforeWhichToInsert) {
        if (anchorBeforeWhichToInsert == null || anchorBeforeWhichToInsert.getParent().isEmpty()) {
            log.warn("Header actions were not installed: anchor component has no parent");
            return null;
        }
        HeaderActionsBar bar = new HeaderActionsBar(
                messages,
                unreadCountService,
                (close, changed) -> new NotificationPanel(
                        notificationService, messages, this::canOpenView, close, changed),
                changed -> new EmailInboxPanel(emailInboxService, unreadCountService, messages, changed));

        com.vaadin.flow.component.Component parent = anchorBeforeWhichToInsert.getParent().get();
        int index = parent.getElement().indexOfChild(anchorBeforeWhichToInsert.getElement());
        parent.getElement().insertChild(Math.max(index, 0), bar.getElement());
        return bar;
    }

    /** Whether the current user may open the given view (checked again by the router). */
    private boolean canOpenView(String viewId) {
        UiShowViewContext context = new UiShowViewContext(viewId);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }
}
