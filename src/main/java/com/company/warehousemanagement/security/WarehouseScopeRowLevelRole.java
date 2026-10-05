package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

@RowLevelRole(
        name = "Warehouse Scope",
        code = WarehouseScopeRowLevelRole.CODE
)
public interface WarehouseScopeRowLevelRole {

    String CODE = "warehouse-scope";

    /*
     * =========================
     * WAREHOUSE
     * =========================
     *
     * Chỉ nhìn thấy Warehouse được
     * cấp cho current user.
     */
    @JpqlRowLevelPolicy(
            entityClass = Warehouse.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where uw.warehouse = {E}
                          and uw.user.id = :current_user_id
                    )
                    """
    )
    void warehouse();

    /*
     * =========================
     * INVENTORY
     * =========================
     */
    @JpqlRowLevelPolicy(
            entityClass = Inventory.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where uw.warehouse = {E}.warehouse
                          and uw.user.id = :current_user_id
                    )
                    """
    )
    void inventory();

    /*
     * =========================
     * INVENTORY MOVEMENT
     * =========================
     */
    @JpqlRowLevelPolicy(
            entityClass = InventoryMovement.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where uw.warehouse = {E}.warehouse
                          and uw.user.id = :current_user_id
                    )
                    """
    )
    void inventoryMovement();

    /*
     * =========================
     * WAREHOUSE TRANSACTION
     * =========================
     *
     * User được nhìn transaction nếu:
     *
     * source warehouse thuộc scope
     * OR
     * destination warehouse thuộc scope
     */
    @JpqlRowLevelPolicy(
            entityClass = WarehouseTransaction.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where uw.warehouse = {E}.sourceWarehouse
                          and uw.user.id = :current_user_id
                    )
                    or
                    exists (
                        select uw2.id
                        from UserWarehouse uw2
                        where uw2.warehouse = {E}.destinationWarehouse
                          and uw2.user.id = :current_user_id
                    )
                    """
    )
    void warehouseTransaction();

    /*
     * =========================
     * TRANSACTION ITEM
     * =========================
     *
     * Item không có Warehouse trực tiếp,
     * nên đi qua transaction.
     */
    @JpqlRowLevelPolicy(
            entityClass = TransactionItem.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where (
                            uw.warehouse = {E}.transaction.sourceWarehouse
                            or
                            uw.warehouse = {E}.transaction.destinationWarehouse
                        )
                        and uw.user.id = :current_user_id
                    )
                    """
    )
    void transactionItem();
}