package com.company.warehousemanagement.ui.header;

import com.company.warehousemanagement.email.EmailInboxException;
import com.company.warehousemanagement.email.EmailInboxPage;
import com.company.warehousemanagement.email.EmailInboxService;
import com.company.warehousemanagement.email.EmailMessageDetail;
import com.company.warehousemanagement.email.EmailMessageSummary;
import com.company.warehousemanagement.service.UnreadCountService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * Panel behind the envelope icon: the real incoming mailbox, read through
 * {@link EmailInboxService}. It is a different data source and a different component from
 * the website notifications. When the mailbox is not configured it says so and shows
 * nothing - it never fabricates mail.
 */
public class EmailInboxPanel extends Div {

    private static final int PAGE_SIZE = 15;

    private final EmailInboxService service;
    private final UnreadCountService unreadCountService;
    private final HeaderMessages msg;
    private final Runnable onUnreadChanged;

    private final Span title = new Span();
    private final Button refreshButton = new Button(new Icon(VaadinIcon.REFRESH));
    private final TextField search = new TextField();
    private final Div list = new Div();
    private final Div footer = new Div();
    private final Div detailView = new Div();
    private final Div listView = new Div();

    private int loaded;
    private String currentQuery = "";

    public EmailInboxPanel(EmailInboxService service,
                           UnreadCountService unreadCountService,
                           HeaderMessages msg,
                           Runnable onUnreadChanged) {
        this.service = service;
        this.unreadCountService = unreadCountService;
        this.msg = msg;
        this.onUnreadChanged = onUnreadChanged;

        addClassNames("khoflow-popover-content", "khoflow-panel");

        title.addClassName("khoflow-panel-title");
        Div heading = new Div(title);
        heading.addClassName("khoflow-panel-heading");

        refreshButton.getElement().setAttribute("theme", "tertiary small");
        refreshButton.addClassName("khoflow-panel-action");
        refreshButton.addClickListener(event -> reload());

        Div header = new Div(heading, refreshButton);
        header.addClassName("khoflow-panel-header");

        search.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(500);
        search.addClassName("khoflow-panel-search");
        search.addValueChangeListener(event -> {
            currentQuery = event.getValue() == null ? "" : event.getValue().trim();
            reload();
        });

        list.addClassName("khoflow-panel-list");
        footer.addClassName("khoflow-panel-footer");
        listView.addClassName("khoflow-panel-view");
        listView.add(search, list, footer);

        detailView.addClassNames("khoflow-panel-view", "khoflow-mail-detail");
        detailView.setVisible(false);

        add(header, listView, detailView);
        applyTexts();
    }

    public void applyTexts() {
        title.setText(msg.get("email.title"));
        search.setPlaceholder(msg.get("email.search"));
        search.getElement().setAttribute("aria-label", msg.get("email.search"));
        refreshButton.setTooltipText(msg.get("email.refresh"));
        refreshButton.getElement().setAttribute("aria-label", msg.get("email.refresh"));
    }

    /** Reloads the first page (and leaves the detail view). Called each time the panel opens. */
    public void reload() {
        applyTexts();
        showList();
        loaded = 0;
        footer.removeAll();

        if (!service.isConfigured()) {
            search.setVisible(false);
            showMessageState(msg.get("email.notConfigured"), msg.get("email.notConfiguredHint"));
            return;
        }
        if (!service.canAccess()) {
            search.setVisible(false);
            showMessageState(msg.get("email.noPermission"), null);
            return;
        }
        search.setVisible(true);
        loadNextPage(true);
    }

    private void loadNextPage(boolean replace) {
        try {
            EmailInboxPage page = service.list(loaded, PAGE_SIZE, currentQuery);
            if (replace) {
                list.removeAll();
            }
            footer.removeAll();
            for (EmailMessageSummary item : page.items()) {
                list.add(createRow(item));
            }
            loaded += page.items().size();

            if (loaded == 0) {
                showMessageState(msg.get("email.empty"), null);
            } else if (page.hasMore()) {
                Button more = new Button(msg.get("common.loadMore"), event -> loadNextPage(false));
                more.getElement().setAttribute("theme", "tertiary small");
                footer.add(more);
            }
        } catch (EmailInboxException exception) {
            showFailure(exception);
        } catch (RuntimeException exception) {
            showFailure(new EmailInboxException(EmailInboxException.Reason.OTHER, exception));
        }
    }

    private Div createRow(EmailMessageSummary item) {
        Div row = new Div();
        row.addClassName("khoflow-mail");
        row.getElement().setAttribute("role", "button");
        row.getElement().setAttribute("tabindex", "0");
        if (!item.read()) {
            row.addClassName("khoflow-mail-unread");
        }

        Span sender = new Span(item.from() == null || item.from().isBlank()
                ? msg.get("email.unknownSender") : item.from());
        sender.addClassName("khoflow-mail-sender");

        Span time = new Span(msg.relativeTime(item.receivedAt()));
        time.addClassName("khoflow-mail-time");

        Div top = new Div(sender, time);
        top.addClassName("khoflow-mail-top");

        Span subject = new Span(subjectOf(item.subject()));
        subject.addClassName("khoflow-mail-subject");
        if (item.hasAttachments()) {
            Icon clip = new Icon(VaadinIcon.PAPERCLIP);
            clip.addClassName("khoflow-mail-clip");
            subject.getElement().appendChild(clip.getElement());
        }

        Span preview = new Span(item.preview() == null ? "" : item.preview());
        preview.addClassName("khoflow-mail-preview");

        Span state = new Span(item.read() ? msg.get("common.read") : msg.get("common.unread"));
        state.addClassName("khoflow-visually-hidden");

        Div text = new Div(top, subject, preview, state);
        text.addClassName("khoflow-mail-text");

        Span dot = new Span();
        dot.addClassName("khoflow-unread-dot");
        dot.setVisible(!item.read());

        row.add(text, dot);
        row.addClickListener(event -> openDetail(item));
        row.getElement().addEventListener("keydown", event -> openDetail(item))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        return row;
    }

    private void openDetail(EmailMessageSummary summary) {
        try {
            EmailMessageDetail detail = service.open(summary.uid(), true);
            unreadCountService.invalidateEmailCount();
            onUnreadChanged.run();
            showDetail(detail);
        } catch (EmailInboxException exception) {
            showFailure(exception);
        } catch (RuntimeException exception) {
            showFailure(new EmailInboxException(EmailInboxException.Reason.OTHER, exception));
        }
    }

    private void showDetail(EmailMessageDetail detail) {
        listView.setVisible(false);
        detailView.removeAll();
        detailView.setVisible(true);

        Button back = new Button(msg.get("email.back"), new Icon(VaadinIcon.ARROW_LEFT), event -> {
            showList();
            reload();
        });
        back.getElement().setAttribute("theme", "tertiary small");

        Button toggleRead = new Button();
        toggleRead.getElement().setAttribute("theme", "tertiary small");
        boolean[] read = {detail.read()};
        toggleRead.setText(read[0] ? msg.get("email.markUnread") : msg.get("email.markRead"));
        toggleRead.addClickListener(event -> {
            try {
                service.setRead(detail.uid(), !read[0]);
                read[0] = !read[0];
                toggleRead.setText(read[0] ? msg.get("email.markUnread") : msg.get("email.markRead"));
                unreadCountService.invalidateEmailCount();
                onUnreadChanged.run();
            } catch (RuntimeException exception) {
                showFailure(exception instanceof EmailInboxException e ? e
                        : new EmailInboxException(EmailInboxException.Reason.OTHER, exception));
            }
        });

        Div toolbar = new Div(back, toggleRead);
        toolbar.addClassName("khoflow-mail-toolbar");

        Span subject = new Span(subjectOf(detail.subject()));
        subject.addClassName("khoflow-mail-detail-subject");

        Span from = new Span(msg.get("email.from") + ": "
                + (detail.from() == null || detail.from().isBlank() ? msg.get("email.unknownSender") : detail.from()));
        from.addClassName("khoflow-mail-detail-meta");
        Span time = new Span(msg.relativeTime(detail.receivedAt()));
        time.addClassName("khoflow-mail-detail-meta");

        // Plain text only: never rendered as HTML, so mail content cannot inject markup.
        Div body = new Div();
        body.addClassName("khoflow-mail-detail-body");
        body.setText(detail.body() == null || detail.body().isBlank() ? msg.get("email.noBody") : detail.body());

        detailView.add(toolbar, subject, from, time, body);

        if (!detail.attachmentNames().isEmpty()) {
            Span attachmentsTitle = new Span(msg.get("email.attachments"));
            attachmentsTitle.addClassName("khoflow-mail-detail-meta");
            Div names = new Div();
            names.addClassName("khoflow-mail-attachments");
            for (String name : detail.attachmentNames()) {
                Span chip = new Span(name);
                chip.addClassName("khoflow-mail-attachment");
                names.add(chip);
            }
            detailView.add(attachmentsTitle, names);
        }
    }

    private void showList() {
        detailView.setVisible(false);
        listView.setVisible(true);
    }

    private void showFailure(EmailInboxException exception) {
        showList();
        footer.removeAll();
        switch (exception.getReason()) {
            case NOT_CONFIGURED -> showMessageState(msg.get("email.notConfigured"), msg.get("email.notConfiguredHint"));
            case ACCESS_DENIED -> showMessageState(msg.get("email.noPermission"), null);
            case AUTHENTICATION -> showErrorState(msg.get("email.error.auth"));
            case CONNECTION -> showErrorState(msg.get("email.error.connect"));
            default -> showErrorState(msg.get("common.error"));
        }
    }

    private void showMessageState(String headline, String hint) {
        list.removeAll();
        Div state = new Div();
        state.addClassName("khoflow-panel-state");
        Span headlineSpan = new Span(headline);
        headlineSpan.addClassName("khoflow-panel-state-headline");
        state.add(headlineSpan);
        if (hint != null) {
            state.add(new Span(hint));
        }
        list.add(state);
    }

    private void showErrorState(String text) {
        list.removeAll();
        Div state = new Div();
        state.addClassNames("khoflow-panel-state", "khoflow-panel-state-error");
        state.add(new Span(text));
        Button retry = new Button(msg.get("common.retry"), event -> reload());
        retry.getElement().setAttribute("theme", "small");
        state.add(retry);
        list.add(state);
    }

    private String subjectOf(String subject) {
        return subject == null || subject.isBlank() ? msg.get("email.noSubject") : subject;
    }
}
