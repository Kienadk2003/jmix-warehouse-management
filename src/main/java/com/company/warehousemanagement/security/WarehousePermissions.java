package com.company.warehousemanagement.security;

public final class WarehousePermissions {

    public static final String EDIT_DRAFT = "warehouse.transaction.editDraft";
    public static final String CONFIRM = "warehouse.transaction.confirm";
    public static final String POST = "warehouse.transaction.post";
    public static final String CANCEL = "warehouse.transaction.cancel";
    public static final String REVERSE = "warehouse.transaction.reverse";
    public static final String COUNT_STOCK = "warehouse.stocktake.count";
    public static final String APPROVE_STOCKTAKE = "warehouse.stocktake.approve";

    private WarehousePermissions() {
    }
}
