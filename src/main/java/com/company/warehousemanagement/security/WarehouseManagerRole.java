package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
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

    @SpecificPolicy(resources = {
            WarehousePermissions.CONFIRM,
            WarehousePermissions.POST,
            WarehousePermissions.CANCEL,
            WarehousePermissions.REVERSE,
            WarehousePermissions.APPROVE_STOCKTAKE
    })
    void managerOperations();

    /*
     * POST/REVERSE cập nhật tồn kho và tạo bút toán kho.
     * Chỉ Manager có quyền ghi hai entity kỹ thuật này;
     * Staff vẫn kế thừa quyền READ từ ViewerRole.
     */
    @EntityPolicy(
            entityClass = Inventory.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE
            }
    )
    @EntityAttributePolicy(
            entityClass = Inventory.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void inventoryForPosting();

    @EntityPolicy(
            entityClass = InventoryMovement.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ
            }
    )
    @EntityAttributePolicy(
            entityClass = InventoryMovement.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void inventoryMovementForPosting();
}
