package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@JmixEntity
@Table(name = "PRODUCT", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_PRODUCT_UNQ_CODE", columnNames = "CODE")
}, indexes = {
        @Index(name = "IDX_PRODUCT_NAME", columnList = "NAME")
})
@Entity
public class Product extends AuditedEntity {

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @InstanceName
    @Column(name = "NAME", nullable = false)
    private String name;

    @JoinColumn(name = "CATEGORY_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ProductCategory category;

    @JoinColumn(name = "BASE_UNIT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Unit baseUnit;

    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public Unit getBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(Unit baseUnit) {
        this.baseUnit = baseUnit;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
