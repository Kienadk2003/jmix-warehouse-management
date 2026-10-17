package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import jakarta.persistence.Transient;

@JmixEntity
@Table(
        name = "USER_WAREHOUSE",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "IDX_USER_WAREHOUSE_UNQ",
                        columnNames = {"USER_ID", "WAREHOUSE_ID"}
                )
        },
        indexes = {
                @Index(
                        name = "IDX_USER_WAREHOUSE_USER",
                        columnList = "USER_ID"
                ),
                @Index(
                        name = "IDX_USER_WAREHOUSE_WAREHOUSE",
                        columnList = "WAREHOUSE_ID"
                )
        }
)
@Entity
public class UserWarehouse extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "WAREHOUSE_ID", nullable = false)
    private Warehouse warehouse;

    /*
     * Danh sách loại sản phẩm được phân quyền cho User.
     *
     * Chỉ dùng để HIỂN THỊ trong danh sách.
     * Không lưu vào database.
     */
    @Transient
    @JmixProperty
    private String productCategories;

    /*
     * Product Category được chọn trong form
     * Create / Edit.
     *
     * Đây là thuộc tính tạm thời,
     * không tạo thêm cột trong USER_WAREHOUSE.
     *
     * Khi Save, UserWarehouseDetailView sẽ dùng
     * giá trị này để tạo/cập nhật UserProductCategory.
     */
    @Transient
    @JmixProperty
    private ProductCategory productCategory;


    // =========================
    // USER
    // =========================

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }


    // =========================
    // WAREHOUSE
    // =========================

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }


    // =========================
    // PRODUCT CATEGORIES
    // Dùng cho LIST VIEW
    // =========================

    public String getProductCategories() {
        return productCategories;
    }

    public void setProductCategories(String productCategories) {
        this.productCategories = productCategories;
    }


    // =========================
    // PRODUCT CATEGORY
    // Dùng cho CREATE / EDIT
    // =========================

    public ProductCategory getProductCategory() {
        return productCategory;
    }

    public void setProductCategory(ProductCategory productCategory) {
        this.productCategory = productCategory;
    }


    // =========================
    // INSTANCE NAME
    // =========================

    @InstanceName
    public String getInstanceName() {

        String username =
                user != null
                        ? user.getUsername()
                        : "";

        String warehouseName =
                warehouse != null
                        ? warehouse.getName()
                        : "";

        return username + " - " + warehouseName;
    }
}