package com.company.warehousemanagement.email;

/**
 * Reads and manages the INCOMING mailbox. This is deliberately separate from the
 * website notifications (AppNotificationService) and from the outgoing SMTP service
 * (WarehouseTransactionEmailService): SMTP only sends mail, it cannot list received mail.
 *
 * <p>Methods that return mailbox content verify the current user's permission
 * ({@code warehouse.emailInbox.view}) on the server.</p>
 */
public interface EmailInboxService {

    /** True when the external IMAP settings are present. Says nothing about connectivity. */
    boolean isConfigured();

    /** True when the current user may read the mailbox. */
    boolean canAccess();

    /**
     * Raw unread count straight from the server. Does not read the security context, so it
     * may run on a background thread. The caller must have checked {@link #canAccess()}.
     */
    int fetchUnreadCount();

    /**
     * One page of messages, newest first. {@code query} filters by sender or subject
     * (blank = no filter). Only envelope data and a short preview are loaded.
     */
    EmailInboxPage list(int offset, int limit, String query);

    /** Loads the full message; when {@code markAsRead} is true also sets the SEEN flag. */
    EmailMessageDetail open(String uid, boolean markAsRead);

    void setRead(String uid, boolean read);
}
