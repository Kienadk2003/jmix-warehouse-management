package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.User;
import com.company.warehousemanagement.entity.UserWarehouse;
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
    // Keep nullable warehouse joins inside independent subqueries. Correlated warehouse
    // paths can be rewritten as inner joins on the outer query by the JPQL transformer.
    String SOURCE_TRANSACTIONS = "select scoped.id from WarehouseTransaction scoped, UserWarehouse assignment "
            + "where scoped.sourceWarehouse = assignment.warehouse and assignment.user.id = :current_user_id";
    String DESTINATION_TRANSACTIONS = "select scoped.id from WarehouseTransaction scoped, UserWarehouse assignment "
            + "where scoped.destinationWarehouse = assignment.warehouse and assignment.user.id = :current_user_id";

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
    @JpqlRowLevelPolicy(entityClass = WarehouseTransaction.class,
            where = "{E}.id in (" + SOURCE_TRANSACTIONS + ") or {E}.id in (" + DESTINATION_TRANSACTIONS + ")")
    void warehouseTransaction();

    /*
     * =========================
     * TRANSACTION ITEM
     * =========================
     *
     * Item không có Warehouse trực tiếp,
     * nên đi qua transaction.
     */
    @JpqlRowLevelPolicy(entityClass = TransactionItem.class,
            where = "{E}.transaction.id in (" + SOURCE_TRANSACTIONS + ") or {E}.transaction.id in (" + DESTINATION_TRANSACTIONS + ")")
    void transactionItem();

    /*
     * =========================
     * USER / EMPLOYEE
     * =========================
     *
     * Manager chỉ nhìn thấy User được
     * phân công vào cùng Warehouse với Manager.
     */
    @JpqlRowLevelPolicy(
            entityClass = User.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where uw.user = {E}
                          and exists (
                              select managerUw.id
                              from UserWarehouse managerUw
                              where managerUw.warehouse = uw.warehouse
                                and managerUw.user.id = :current_user_id
                          )
                    )
                    """
    )
    void user();

    /*
     * =========================
     * USER WAREHOUSE
     * =========================
     *
     * Manager chỉ nhìn thấy các bản ghi
     * UserWarehouse thuộc Warehouse của mình.
     */
    @JpqlRowLevelPolicy(
            entityClass = UserWarehouse.class,
            where = """
                    exists (
                        select managerUw.id
                        from UserWarehouse managerUw
                        where managerUw.warehouse = {E}.warehouse
                          and managerUw.user.id = :current_user_id
                    )
                    """
    )
    void userWarehouse();

    /*
     * =========================
     * STOCKTAKE
     * =========================
     * Phiếu kiểm kê chỉ thuộc phạm vi kho được gán cho user.
     */
    @JpqlRowLevelPolicy(
            entityClass = Stocktake.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where uw.warehouse = {E}.warehouse
                          and uw.user.id = :current_user_id
                    )
                    """
    )
    void stocktake();

    @JpqlRowLevelPolicy(
            entityClass = StocktakeItem.class,
            where = """
                    exists (
                        select uw.id
                        from UserWarehouse uw
                        where uw.warehouse = {E}.stocktake.warehouse
                          and uw.user.id = :current_user_id
                    )
                    """
    )
    void stocktakeItem();

    @JpqlRowLevelPolicy(entityClass = com.company.warehousemanagement.entity.TransactionDocument.class,
            where = "{E}.transaction.id in (" + SOURCE_TRANSACTIONS + ") or {E}.transaction.id in (" + DESTINATION_TRANSACTIONS + ")")
    void document();
}
