package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum WarehouseTransactionStatus implements EnumClass<String> {
    DRAFT("DRAFT"),
    CONFIRMED("CONFIRMED"),
    POSTED("POSTED"),
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
        for (WarehouseTransactionStatus value : WarehouseTransactionStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}

