package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum WarehouseTransactionStatus implements EnumClass<String> {

    DRAFT("DRAFT"),
    PENDING_APPROVAL("PENDING_APPROVAL"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    POSTED("POSTED"),

    // Retain legacy states so existing data and older workflows remain readable.
    CONFIRMED("CONFIRMED"),
    CANCELLED("CANCELLED"),
    REVERSED("REVERSED");

    private final String id;

    WarehouseTransactionStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static WarehouseTransactionStatus fromId(String id) {
        if (id == null) {
            return null;
        }
        for (WarehouseTransactionStatus value : WarehouseTransactionStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
