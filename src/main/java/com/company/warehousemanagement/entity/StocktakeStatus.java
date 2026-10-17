package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum StocktakeStatus implements EnumClass<String> {
    DRAFT("DRAFT"),
    COUNTING("COUNTING"),
    PENDING_APPROVAL("PENDING_APPROVAL"),
    REVIEWING("REVIEWING"), // Retained temporarily for legacy records
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED");

    private final String id;

    StocktakeStatus(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static StocktakeStatus fromId(String id) {
        if (id == null) {
            return null;
        }
        for (StocktakeStatus value : StocktakeStatus.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}
