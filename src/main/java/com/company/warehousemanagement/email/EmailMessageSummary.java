package com.company.warehousemanagement.email;

import java.time.Instant;

/** One row of the inbox list. {@code uid} is the IMAP UID, stable across sessions. */
public record EmailMessageSummary(String uid,
                                  String from,
                                  String subject,
                                  String preview,
                                  Instant receivedAt,
                                  boolean read,
                                  boolean hasAttachments) {
}
