package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum MovementType implements EnumClass<String> {
    IMPORT("IMPORT"),
    EXPORT("EXPORT"),
    TRANSFER_IN("TRANSFER_IN"),
    TRANSFER_OUT("TRANSFER_OUT"),
    ADJUSTMENT_IN("ADJUSTMENT_IN"),
    ADJUSTMENT_OUT("ADJUSTMENT_OUT"),
    REVERSAL("REVERSAL");

    private final String id;

    MovementType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static MovementType fromId(String id) {
        for (MovementType value : MovementType.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}

