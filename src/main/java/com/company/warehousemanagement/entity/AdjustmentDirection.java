package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;


import java.util.Arrays;

public enum AdjustmentDirection implements EnumClass<String> {

    IN("IN"),
    OUT("OUT");

    private final String id;

    AdjustmentDirection(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static AdjustmentDirection fromId(String id) {
        return Arrays.stream(values())
                .filter(value -> value.id.equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return id;
    }
}