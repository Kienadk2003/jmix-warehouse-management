package com.company.warehousemanagement.security;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.UserProductCategory;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

@RowLevelRole(
        name = "Staff Product Scope",
        code = StaffProductScopeRowLevelRole.CODE
)
public interface StaffProductScopeRowLevelRole {

    String CODE = "staff-product-scope";

    /*
     * =========================
     * PRODUCT
     * =========================
     *
     * Staff chỉ nhìn thấy Product
     * thuộc ProductCategory được
     * phân công cho current user.
     */
    @JpqlRowLevelPolicy(
            entityClass = Product.class,
            where = """
                    exists (
                        select upc.id
                        from UserProductCategory upc
                        where upc.productCategory = {E}.category
                          and upc.user.id = :current_user_id
                    )
                    """
    )
    void product();

    /*
     * =========================
     * USER PRODUCT CATEGORY
     * =========================
     *
     * Staff chỉ nhìn thấy các
     * phân công của chính mình.
     */
    @JpqlRowLevelPolicy(
            entityClass = UserProductCategory.class,
            where = """
                    {E}.user.id = :current_user_id
                    """
    )
    void userProductCategory();
}