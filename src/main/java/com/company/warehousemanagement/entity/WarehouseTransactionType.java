package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum WarehouseTransactionType implements EnumClass<String> {
    IMPORT("IMPORT"),
    EXPORT("EXPORT"),
    TRANSFER("TRANSFER"),
    ADJUSTMENT("ADJUSTMENT");

    private final String id;

    WarehouseTransactionType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static WarehouseTransactionType fromId(String id) {
        for (WarehouseTransactionType value : WarehouseTransactionType.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}

