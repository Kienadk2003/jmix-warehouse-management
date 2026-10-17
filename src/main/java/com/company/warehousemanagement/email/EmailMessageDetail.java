package com.company.warehousemanagement.email;

import java.time.Instant;
import java.util.List;

/** Full message as plain text. HTML bodies are converted to text, never rendered. */
public record EmailMessageDetail(String uid,
                                 String from,
                                 String subject,
                                 String body,
                                 Instant receivedAt,
                                 boolean read,
                                 List<String> attachmentNames) {
}
