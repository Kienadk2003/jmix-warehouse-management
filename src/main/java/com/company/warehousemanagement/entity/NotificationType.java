package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

/**
 * Types of in-app (website) notifications. Every type is produced by a real
 * workflow transition; see WarehouseNotificationListener.
 */
public enum NotificationType implements EnumClass<String> {

    TRANSACTION_SUBMITTED("TRANSACTION_SUBMITTED"),
    TRANSACTION_APPROVED("TRANSACTION_APPROVED"),
    TRANSACTION_REJECTED("TRANSACTION_REJECTED"),
    STOCKTAKE_SUBMITTED("STOCKTAKE_SUBMITTED"),
    STOCKTAKE_APPROVED("STOCKTAKE_APPROVED"),
    STOCKTAKE_REJECTED("STOCKTAKE_REJECTED");

    private final String id;

    NotificationType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static NotificationType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (NotificationType value : NotificationType.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
