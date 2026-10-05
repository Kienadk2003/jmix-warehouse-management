package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(
        name = "Warehouse Staff",
        code = WarehouseStaffRole.CODE,
        scope = SecurityScope.UI
)
public interface WarehouseStaffRole extends ViewerRole {

    String CODE = "warehouse-staff";

    /*
     * Staff được mở màn hình nghiệp vụ
     */
    @ViewPolicy(viewIds = {
            "ImportReceipt.list",
            "ImportReceipt.detail",
            "WarehouseTransaction.exportList",
            "WarehouseTransaction.exportDetail",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.transferDetail",
            "WarehouseTransaction.adjustmentList",
            "WarehouseTransaction.adjustmentDetail",
            "TransactionItem.detail",
            "Warehouse.list",
            "Partner.list",
            "Product.list",
            "Unit.list"
    })
    @MenuPolicy(menuIds = {
            "ImportReceipt.list",
            "WarehouseTransaction.exportList",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.adjustmentList"
    })
    void transactionViews();

    /*
     * =========================
     * TRANSACTION
     *
     * Staff:
     * CREATE
     * READ
     * UPDATE
     *
     * Không DELETE transaction.
     * =========================
     */

    @EntityPolicy(
            entityClass = WarehouseTransaction.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE
            }
    )
    @EntityAttributePolicy(
            entityClass = WarehouseTransaction.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void transaction();

    /*
     * =========================
     * TRANSACTION ITEM
     *
     * Cho phép sửa/xóa line của DRAFT.
     * Business service sẽ đảm bảo chỉ DRAFT được sửa.
     * =========================
     */

    @EntityPolicy(
            entityClass = TransactionItem.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE,
                    EntityPolicyAction.DELETE
            }
    )
    @EntityAttributePolicy(
            entityClass = TransactionItem.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void transactionItem();

    /*
     * =========================
     * STOCKTAKE
     * =========================
     */

    @EntityPolicy(
            entityClass = Stocktake.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE
            }
    )
    @EntityAttributePolicy(
            entityClass = Stocktake.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void stocktake();

    @EntityPolicy(
            entityClass = StocktakeItem.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE,
                    EntityPolicyAction.DELETE
            }
    )
    @EntityAttributePolicy(
            entityClass = StocktakeItem.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void stocktakeItem();

    @SpecificPolicy(resources = {
            WarehousePermissions.EDIT_DRAFT,
            WarehousePermissions.COUNT_STOCK
    })
    void staffOperations();
}
