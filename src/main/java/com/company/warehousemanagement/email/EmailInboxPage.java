package com.company.warehousemanagement.email;

import java.util.List;

public record EmailInboxPage(List<EmailMessageSummary> items, boolean hasMore) {
}
