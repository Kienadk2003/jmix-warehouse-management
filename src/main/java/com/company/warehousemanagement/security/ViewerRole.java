package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.ProductCategory;
import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.TransactionDocument;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.entity.Warehouse;
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
        name = "Warehouse Viewer",
        code = ViewerRole.CODE,
        scope = SecurityScope.UI
)
public interface ViewerRole extends UiMinimalRole {

    String CODE = "warehouse-viewer";

    /*
     * UI VIEWS
     *
     * Viewer chỉ xem dữ liệu.
     * Bổ sung các màn hình nghiệp vụ đã gộp:
     * - Nhập / Xuất kho
     * - Chuyển kho
     * - Kiểm kê / Điều chỉnh
     */
    @ViewPolicy(viewIds = {
            // Tra cứu tồn kho
            "Inventory.list",
            "Inventory.stockCard",
            "Inventory.report",

            // Các màn hình nghiệp vụ mới
            "WarehouseTransaction.importExportList",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.stocktakeAdjustmentList",

            // Các màn hình cũ nếu vẫn còn sử dụng
            "ImportReceipt.list",
            "ImportReceipt.detail",
            "WarehouseTransaction.exportList",
            "WarehouseTransaction.transferDetail",
            "WarehouseTransaction.adjustmentList",
            "WarehouseTransaction.exportDetail",
            "WarehouseTransaction.adjustmentDetail",

            // Kiểm kê
            "Stocktake.list",
            "Stocktake.detail"
    })
    void views();

    /*
     * MENU
     *
     * ID ở đây phải khớp với thuộc tính view
     * của các item trong menu.xml.
     *
     * Không cấp menu Danh sách chờ duyệt cho Viewer.
     */
    @MenuPolicy(menuIds = {
            // Tồn kho
            "Inventory.list",
            "Inventory.stockCard",
            "Inventory.report",

            // Nghiệp vụ kho - các màn hình đã gộp
            "WarehouseTransaction.importExportList",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.stocktakeAdjustmentList",

            // Báo cáo
            "Warehouse.reports",
            "Stocktake.list"
    })
    void menus();

    /*
     * REPORTS
     */
    @ViewPolicy(viewIds = {
            "Warehouse.reports"
    })
    @MenuPolicy(menuIds = {
            "Warehouse.reports"
    })
    void reports();

    /*
     * TRANSACTION DOCUMENTS
     *
     * Chỉ đọc chứng từ đính kèm nếu có quyền xem tài liệu.
     */
    @SpecificPolicy(
            resources = WarehousePermissions.VIEW_DOCUMENTS
    )
    @EntityPolicy(
            entityClass = TransactionDocument.class,
            actions = EntityPolicyAction.READ
    )
    @EntityAttributePolicy(
            entityClass = TransactionDocument.class,
            attributes = "*",
            action = EntityAttributePolicyAction.VIEW
    )
    void documents();

    /*
     * MASTER DATA - READ ONLY
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
     * INVENTORY / LEDGER - READ ONLY
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
     * TRANSACTIONS - READ ONLY
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
     * STOCKTAKE - READ ONLY
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