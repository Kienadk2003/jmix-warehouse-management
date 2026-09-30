package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum PartnerType implements EnumClass<String> {
    SUPPLIER("SUPPLIER"),
    CUSTOMER("CUSTOMER"),
    BOTH("BOTH");

    private final String id;

    PartnerType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public static PartnerType fromId(String id) {
        for (PartnerType value : PartnerType.values()) {
            if (value.getId().equals(id)) {
                return value;
            }
        }
        return null;
    }
}

