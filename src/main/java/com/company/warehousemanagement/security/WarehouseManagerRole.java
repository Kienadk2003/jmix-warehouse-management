package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(
        name = "Warehouse Manager",
        code = WarehouseManagerRole.CODE,
        scope = SecurityScope.UI
)
public interface WarehouseManagerRole extends WarehouseStaffRole {

    String CODE = "warehouse-manager";

    /*
     * Manager dùng cùng các view với Staff,
     * nhưng Manager có quyền nghiệp vụ cao hơn ở service layer.
     */

    @ViewPolicy(viewIds = {
            "ImportReceipt.list",
            "ImportReceipt.detail",
            "WarehouseTransaction.exportList",
            "WarehouseTransaction.exportDetail",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.transferDetail",
            "WarehouseTransaction.adjustmentList",
            "WarehouseTransaction.adjustmentDetail"
    })
    void managerViews();

    /*
     * Manager được sửa transaction theo quyền entity.
     * POST / CANCEL / REVERSE sẽ được bảo vệ thêm
     * ở backend service.
     */

    @EntityPolicy(
            entityClass = WarehouseTransaction.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE
            }
    )
    void transaction();

    @EntityPolicy(
            entityClass = TransactionItem.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE,
                    EntityPolicyAction.DELETE
            }
    )
    void transactionItem();

    @EntityPolicy(
            entityClass = Stocktake.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE
            }
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
    void stocktakeItem();
}