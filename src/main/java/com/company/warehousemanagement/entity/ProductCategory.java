package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@JmixEntity
@Table(name = "PRODUCT_CATEGORY", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_PRODUCT_CATEGORY_UNQ_CODE", columnNames = "CODE")
})
@Entity
public class ProductCategory extends AuditedEntity {

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @InstanceName
    @Column(name = "NAME", nullable = false)
    private String name;

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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

