package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.ProductCategory;
import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(
        name = "Warehouse Viewer",
        code = ViewerRole.CODE,
        scope = SecurityScope.UI
)
public interface ViewerRole extends UiMinimalRole {

    String CODE = "warehouse-viewer";

    /*
     * =========================
     * UI VIEWS
     * =========================
     */

    @ViewPolicy(viewIds = {
            "Inventory.list",
            "Inventory.stockCard",
            "Inventory.report",
            "ImportReceipt.list",
            "WarehouseTransaction.exportList",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.adjustmentList",
            "WarehouseTransaction.exportDetail",
            "WarehouseTransaction.transferDetail",
            "WarehouseTransaction.adjustmentDetail"
    })
    @MenuPolicy(menuIds = {
            "Inventory.list",
            "Inventory.stockCard",
            "Inventory.report",
            "ImportReceipt.list",
            "WarehouseTransaction.exportList",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.adjustmentList"
    })
    void views();

    /*
     * =========================
     * MASTER DATA - READ ONLY
     * =========================
     */

    @EntityPolicy(
            entityClass = Product.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = Product.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void product();

    @EntityPolicy(
            entityClass = ProductCategory.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = ProductCategory.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void productCategory();

    @EntityPolicy(
            entityClass = Unit.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = Unit.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void unit();

    @EntityPolicy(
            entityClass = Partner.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = Partner.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void partner();

    @EntityPolicy(
            entityClass = Warehouse.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = Warehouse.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void warehouse();

    /*
     * =========================
     * INVENTORY / LEDGER - READ
     * =========================
     */

    @EntityPolicy(
            entityClass = Inventory.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = Inventory.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void inventory();

    @EntityPolicy(
            entityClass = InventoryMovement.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = InventoryMovement.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void inventoryMovement();

    /*
     * =========================
     * TRANSACTIONS - READ
     * =========================
     */

    @EntityPolicy(
            entityClass = WarehouseTransaction.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = WarehouseTransaction.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void warehouseTransaction();

    @EntityPolicy(
            entityClass = TransactionItem.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = TransactionItem.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void transactionItem();

    /*
     * =========================
     * STOCKTAKE - READ
     * =========================
     */

    @EntityPolicy(
            entityClass = Stocktake.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = Stocktake.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void stocktake();

    @EntityPolicy(
            entityClass = StocktakeItem.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = StocktakeItem.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void stocktakeItem();
}