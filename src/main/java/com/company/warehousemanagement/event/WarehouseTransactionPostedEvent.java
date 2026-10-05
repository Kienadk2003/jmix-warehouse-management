package com.company.warehousemanagement.event;

import java.util.UUID;

public record WarehouseTransactionPostedEvent(
        UUID transactionId
) {
}