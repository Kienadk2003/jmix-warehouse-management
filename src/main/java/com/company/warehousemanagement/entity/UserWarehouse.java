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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    @InstanceName
    public String getInstanceName() {
        String username = user != null ? user.getUsername() : "";
        String warehouseName = warehouse != null ? warehouse.getName() : "";
        return username + " - " + warehouseName;
    }
}