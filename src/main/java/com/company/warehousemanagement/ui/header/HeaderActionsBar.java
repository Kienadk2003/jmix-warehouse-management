package com.company.warehousemanagement.ui.header;

import com.company.warehousemanagement.service.UnreadCountService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.shared.Registration;

import java.util.OptionalLong;

/**
 * The two header icons: [bell] website notifications, [envelope] email inbox.
 * They sit immediately left of the authenticated user menu and each has its own badge, panel and data
 * source. Only one panel can be open at a time.
 */
public class HeaderActionsBar extends Div implements LocaleChangeObserver {

    private static final int POLL_INTERVAL_MS = 30_000;

    private final HeaderMessages msg;
    private final UnreadCountService unreadCountService;

    private final HeaderIconButton bell = new HeaderIconButton(VaadinIcon.BELL_O, "warehouse-header-bell");
    private final HeaderIconButton mail = new HeaderIconButton(VaadinIcon.ENVELOPE_O, "warehouse-header-mail");
    private final Popover bellPopover = new Popover();
    private final Popover mailPopover = new Popover();
    private final NotificationPanel notificationPanel;
    private final EmailInboxPanel emailPanel;

    private Registration pollRegistration;
    private boolean pollingStartedByUs;

    public HeaderActionsBar(HeaderMessages msg,
                            UnreadCountService unreadCountService,
                            NotificationPanelFactory notificationPanelFactory,
                            EmailPanelFactory emailPanelFactory) {
        this.msg = msg;
        this.unreadCountService = unreadCountService;

        addClassName("warehouse-header-actions");

        notificationPanel = notificationPanelFactory.create(
                () -> bellPopover.setOpened(false), this::refreshBadges);
        emailPanel = emailPanelFactory.create(this::refreshBadges);

        configurePopover(bellPopover, bell, notificationPanel);
        configurePopover(mailPopover, mail, emailPanel);

        bellPopover.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                mailPopover.setOpened(false);      // one panel at a time
                notificationPanel.reload();
                refreshBadges();
            }
        });
        mailPopover.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                bellPopover.setOpened(false);
                emailPanel.reload();
                refreshBadges();
            }
        });

        add(bell, mail);
        applyTexts();
    }

    /** Factories so the bar does not depend on Spring services directly. */
    @FunctionalInterface
    public interface NotificationPanelFactory {
        NotificationPanel create(Runnable closePanel, Runnable onUnreadChanged);
    }

    @FunctionalInterface
    public interface EmailPanelFactory {
        EmailInboxPanel create(Runnable onUnreadChanged);
    }

    private void configurePopover(Popover popover, HeaderIconButton icon, com.vaadin.flow.component.Component panel) {
        popover.setTarget(icon.getButton());
        popover.setOpenOnClick(true);
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.setWidth("400px");
        panel.getElement().getStyle().set("max-width", "calc(100vw - 16px)");
        popover.add(panel);
    }

    private void applyTexts() {
        bell.setLabel(msg.get("notifications.title"));
        mail.setLabel(msg.get("email.title"));
    }

    /** Re-reads both counts from their own sources. The email count comes from a cache. */
    public void refreshBadges() {
        try {
            bell.setCount(unreadCountService.getWebsiteUnreadCount());
        } catch (RuntimeException exception) {
            bell.setCount(0);
        }
        try {
            OptionalLong emailUnread = unreadCountService.getEmailUnreadCount();
            mail.setCount(emailUnread.isPresent() ? emailUnread.getAsLong() : 0);
        } catch (RuntimeException exception) {
            mail.setCount(0);
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        applyTexts();
        notificationPanel.applyTexts();
        emailPanel.applyTexts();
        if (bellPopover.isOpened()) {
            notificationPanel.reload();
        }
        if (mailPopover.isOpened()) {
            emailPanel.reload();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();
        refreshBadges();

        // Light polling keeps the badges current. Only started if nothing else already polls,
        // and undone on detach so we never change the interval used by other components.
        if (ui.getPollInterval() <= 0) {
            ui.setPollInterval(POLL_INTERVAL_MS);
            pollingStartedByUs = true;
        }
        pollRegistration = ui.addPollListener(event -> refreshBadges());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        if (pollingStartedByUs) {
            detachEvent.getUI().setPollInterval(-1);
            pollingStartedByUs = false;
        }
        super.onDetach(detachEvent);
    }
}
