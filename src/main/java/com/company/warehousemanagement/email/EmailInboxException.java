package com.company.warehousemanagement.email;

/**
 * Failure while reading the mailbox. The {@link Reason} lets the UI show an accurate,
 * localized state without exposing server details or credentials.
 */
public class EmailInboxException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        ACCESS_DENIED,
        AUTHENTICATION,
        CONNECTION,
        NOT_FOUND,
        OTHER
    }

    private final Reason reason;

    public EmailInboxException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public EmailInboxException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
