package com.company.warehousemanagement.security;

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
     * ALL ENTITY CRUD
     * =========================
     */

    @EntityPolicy(
            entityName = "*",
            actions = EntityPolicyAction.ALL
    )
    @EntityAttributePolicy(
            entityName = "*",
            attributes = "*",
            action = EntityAttributePolicyAction.MODIFY
    )
    void entities();

    /*
     * =========================
     * ALL VIEWS + MENUS
     * =========================
     */

    @ViewPolicy(viewIds = "*")
    void views();

    @MenuPolicy(menuIds = "*")
    void menus();

    /*
     * Cho phép ADMIN dùng các cơ chế Security UI
     * như Role Assignments.
     */
    @SpecificPolicy(resources = "*")
    void securityFunctions();
}