package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.TransactionItem;
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
        name = "Warehouse Staff",
        code = WarehouseStaffRole.CODE,
        scope = SecurityScope.UI
)
public interface WarehouseStaffRole extends ViewerRole {

    String CODE = "warehouse-staff";

    /*
     * STAFF - VIEW ACCESS
     *
     * Các màn hình nghiệp vụ hiện tại:
     * - Nhập / Xuất kho
     * - Chuyển kho
     * - Kiểm kê / Điều chỉnh
     *
     * Giữ các view cũ nếu chúng vẫn tồn tại trong dự án
     * và còn được sử dụng bởi luồng nghiệp vụ.
     */
    @ViewPolicy(viewIds = {
            "MainView",

            // Các màn hình nghiệp vụ đã gộp
            "WarehouseTransaction.importExportList",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.stocktakeAdjustmentList",

            // Các màn hình chi tiết / view cũ
            "ImportReceipt.list",
            "ImportReceipt.detail",

            "WarehouseTransaction.exportList",
            "WarehouseTransaction.exportDetail",

            "WarehouseTransaction.transferDetail",

            "WarehouseTransaction.adjustmentList",
            "WarehouseTransaction.adjustmentDetail",

            "Stocktake.list",
            "Stocktake.detail",

            "TransactionItem.detail",

            // Các màn hình dữ liệu nền
            "Warehouse.list",
            "Partner.list",
            "Product.list",
            "Unit.list"
    })
    void staffViews();

    /*
     * STAFF - MENU ACCESS
     *
     * Các ID dưới đây phải khớp với thuộc tính view
     * của item tương ứng trong menu.xml.
     *
     * Không thêm WarehouseTransaction.pendingApprovals:
     * Staff không có quyền duyệt chứng từ.
     */
    @MenuPolicy(menuIds = {
            "MainView",

            "WarehouseTransaction.importExportList",
            "WarehouseTransaction.transferList",
            "WarehouseTransaction.stocktakeAdjustmentList"
    })
    void staffMenus();

    /*
     * WAREHOUSE TRANSACTION
     *
     * Staff được CREATE, READ, UPDATE chứng từ.
     * Không cấp quyền DELETE trực tiếp chứng từ.
     *
     * Quy tắc chỉ được sửa phiếu DRAFT phải được
     * kiểm tra thêm tại service nghiệp vụ.
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
     * TRANSACTION ITEM
     *
     * Cho phép thêm, xem, sửa và xóa dòng hàng.
     * Service phải kiểm tra phiếu còn ở trạng thái
     * cho phép chỉnh sửa trước khi thực hiện thao tác.
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
     * STOCKTAKE
     *
     * Staff được tạo, xem và cập nhật phiếu kiểm kê.
     * Không cấp quyền duyệt kiểm kê tại đây.
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

    /*
     * STOCKTAKE ITEM
     *
     * Cho phép tạo, xem, sửa và xóa dòng kiểm kê.
     * Service phải kiểm soát trạng thái phiếu trước
     * khi cho phép thay đổi số lượng đã kiểm kê.
     */
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

    /*
     * STAFF - BUSINESS PERMISSIONS
     *
     * EDIT_DRAFT: chỉnh sửa phiếu nháp.
     * COUNT_STOCK: nhập số đếm kiểm kê.
     * SUBMIT: gửi phiếu để chờ duyệt.
     *
     * Không cấp APPROVE, REJECT, POST hoặc REVERSE.
     */
    @SpecificPolicy(resources = {
            WarehousePermissions.EDIT_DRAFT,
            WarehousePermissions.COUNT_STOCK,
            WarehousePermissions.SUBMIT
    })
    void staffOperations();
}