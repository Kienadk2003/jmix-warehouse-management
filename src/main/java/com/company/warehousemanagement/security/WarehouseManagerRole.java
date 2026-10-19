package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.User;
import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(
        name = "Warehouse Manager",
        code = WarehouseManagerRole.CODE,
        scope = SecurityScope.UI
)
public interface WarehouseManagerRole extends ViewerRole {

    String CODE = "warehouse-manager";

    /*
     * Manager xem và xử lý phiếu đã gửi; không kế thừa quyền
     * EDIT_DRAFT, SUBMIT hoặc COUNT_STOCK của WarehouseStaffRole.
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
            // Combined operational views added to the sidebar
            "WarehouseTransaction.importExportList",
            "WarehouseTransaction.pendingApprovals",
            "WarehouseTransaction.stocktakeAdjustmentList",
            "Stocktake.list",
            "Stocktake.detail",
            "User.list",
            "User.detail",
            "UserWarehouse.list",
            "UserWarehouse.detail"
    })
    void managerViews();

    @MenuPolicy(menuIds = {
            // Nghiệp vụ kho
            "WarehouseTransaction.importExportList",
            "WarehouseTransaction.pendingApprovals",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.stocktakeAdjustmentList",
            // Menu quản trị được phép cho Manager
            "Stocktake.list",
            "User.list",
            "UserWarehouse.list"
    })
    void managerMenus();

    @SpecificPolicy(resources = {
            WarehousePermissions.POST,
            WarehousePermissions.REVERSE,
            WarehousePermissions.APPROVE,
            WarehousePermissions.REJECT,
            WarehousePermissions.APPROVE_STOCKTAKE,
            WarehousePermissions.REJECT_STOCKTAKE,
            WarehousePermissions.VIEW_EMAIL_INBOX
    })
    void managerOperations();

    /*
     * Dữ liệu tồn chỉ được ghi qua các service nghiệp vụ POST/REVERSE.
     * Manager không có quyền DELETE Inventory hoặc sửa trực tiếp ledger.
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
            attributes = {"warehouse", "product", "quantity", "reservedQuantity", "updatedAt"},
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
            attributes = {"transaction", "item", "warehouse", "product", "occurredAt", "signedQuantity", "movementType", "sequenceNo"},
            action = EntityAttributePolicyAction.MODIFY
    )
    void inventoryMovementForPosting();

    /*
     * Manager chỉ được cập nhật các trường workflow; không có CREATE
     * WarehouseTransaction ở entity policy để không tạo phiếu nháp.
     * ReversalService lưu phiếu reversal nội bộ sau khi kiểm tra REVERSE.
     */
    @EntityPolicy(
            entityClass = WarehouseTransaction.class,
            actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE}
    )
    @EntityAttributePolicy(
            entityClass = WarehouseTransaction.class,
            attributes = {"status", "reason", "postedAt", "postedBy"},
            action = EntityAttributePolicyAction.MODIFY
    )
    void transactionWorkflow();

    /*
     * Khi Jmix lưu WarehouseTransaction dạng composition, quá trình lưu có
     * thể kiểm tra quyền UPDATE của các TransactionItem hiện có. Chỉ cấp
     * quyền entity UPDATE để hỗ trợ lưu workflow; không cấp quyền MODIFY
     * thuộc tính ở đây và không cấp CREATE/DELETE TransactionItem.
     */
    @EntityPolicy(
            entityClass = TransactionItem.class,
            actions = {EntityPolicyAction.UPDATE}
    )
    void transactionItemUpdateForWorkflow();

    /*
     * Manager chỉ cập nhật trạng thái/thời điểm duyệt kiểm kê.
     * Việc ghi Inventory/Movement do StocktakeService kiểm soát.
     */
    @EntityPolicy(
            entityClass = Stocktake.class,
            actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE}
    )
    @EntityAttributePolicy(
            entityClass = Stocktake.class,
            attributes = {"status", "approvedAt"},
            action = EntityAttributePolicyAction.MODIFY
    )
    void stocktakeApproval();

    /*
     * Quản lý hồ sơ nhân viên. Thuộc tính password là trường system/secret;
     * không cấp quyền thay đổi thuộc tính liên quan đến phân quyền.
     */
    @EntityPolicy(
            entityClass = User.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE
            }
    )
    @EntityAttributePolicy(
            entityClass = User.class,
            attributes = {"username", "password", "firstName", "lastName", "email", "active", "timeZoneId"},
            action = EntityAttributePolicyAction.MODIFY
    )
    void employeeManagement();

    @EntityPolicy(
            entityClass = UserWarehouse.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE,
                    EntityPolicyAction.DELETE
            }
    )
    @EntityAttributePolicy(
            entityClass = UserWarehouse.class,
            attributes = {"user", "warehouse", "productCategories", "productCategory"},
            action = EntityAttributePolicyAction.MODIFY
    )
    void employeeWarehouseAssignment();
}
