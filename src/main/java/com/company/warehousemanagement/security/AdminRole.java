package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.ProductCategory;
import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.entity.User;
import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.entity.Warehouse;
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
        name = "Administrator",
        code = AdminRole.CODE,
        scope = SecurityScope.UI
)
public interface AdminRole extends UiMinimalRole {

    String CODE = "admin";

    /*
     * =========================
     * USER MANAGEMENT
     * =========================
     *
     * ADMIN được:
     * - tạo user
     * - xem user
     * - sửa user
     * - xóa user
     */
    @EntityPolicy(
            entityClass = User.class,
            actions = {
                    EntityPolicyAction.CREATE,
                    EntityPolicyAction.READ,
                    EntityPolicyAction.UPDATE,
                    EntityPolicyAction.DELETE
            }
    )
    @EntityAttributePolicy(
            entityClass = User.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void userManagement();

    /*
     * =========================
     * USER -> WAREHOUSE
     * =========================
     *
     * ADMIN được quản lý việc gán
     * user vào warehouse.
     */
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
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void userWarehouseManagement();

    /*
     * =========================
     * MASTER DATA
     * =========================
     */

    @EntityPolicy(
            entityClass = Product.class,
            actions = EntityPolicyAction.ALL
    )
    @EntityAttributePolicy(
            entityClass = Product.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void product();

    @EntityPolicy(
            entityClass = ProductCategory.class,
            actions = EntityPolicyAction.ALL
    )
    @EntityAttributePolicy(
            entityClass = ProductCategory.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void productCategory();

    @EntityPolicy(
            entityClass = Unit.class,
            actions = EntityPolicyAction.ALL
    )
    @EntityAttributePolicy(
            entityClass = Unit.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void unit();

    @EntityPolicy(
            entityClass = Warehouse.class,
            actions = EntityPolicyAction.ALL
    )
    @EntityAttributePolicy(
            entityClass = Warehouse.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void warehouse();

    @EntityPolicy(
            entityClass = Partner.class,
            actions = EntityPolicyAction.ALL
    )
    @EntityAttributePolicy(
            entityClass = Partner.class,
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void partner();

    /*
     * =========================
     * ADMIN UI
     * =========================
     *
     * Chỉ mở các màn hình thuộc:
     * - User
     * - User Warehouse
     * - Master Data
     *
     * Không cấp trực tiếp các màn hình
     * nghiệp vụ kho như Export / Transfer /
     * Adjustment.
     */
    @ViewPolicy(viewIds = {
            "User.list",
            "User.detail",

            "UserWarehouse.list",
            "UserWarehouse.detail",

            "Product.list",
            "Product.detail",

            "ProductCategory.list",
            "ProductCategory.detail",

            "Unit.list",
            "Unit.detail",

            "Partner.list",
            "Partner.detail",

            "Warehouse.list",
            "Warehouse.detail"
    })
    void administrationViews();

    @MenuPolicy(menuIds = {
            "User.list",
            "UserWarehouse.list",

            "ProductCategory.list",
            "Product.list",
            "Unit.list",
            "Partner.list",
            "Warehouse.list"
    })
    void administrationMenus();

    /*
     * =========================
     * SECURITY / TECHNICAL
     * =========================
     *
     * ADMIN cần dùng các chức năng Security
     * như Role Assignment trong User List.
     *
     * Đây là quyền kỹ thuật cao dành cho
     * ADMIN nghiệp vụ, nhưng KHÔNG đồng nghĩa
     * với system-full-access.
     */
    @SpecificPolicy(resources = "*")
    void technicalSecurity();
}