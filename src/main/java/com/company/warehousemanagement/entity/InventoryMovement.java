package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JmixEntity
@Table(name = "INVENTORY_MOVEMENT", indexes = {
        @Index(name = "IDX_INV_MOVEMENT_WAREHOUSE_PRODUCT_TIME", columnList = "WAREHOUSE_ID, PRODUCT_ID, OCCURRED_AT"),
        @Index(name = "IDX_INV_MOVEMENT_TRANSACTION", columnList = "TRANSACTION_ID")
})
@Entity
public class InventoryMovement extends BaseUuidEntity {

    @JoinColumn(name = "TRANSACTION_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private WarehouseTransaction transaction;

    @JoinColumn(name = "ITEM_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TransactionItem item;

    @JoinColumn(name = "WAREHOUSE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Warehouse warehouse;

    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(name = "OCCURRED_AT", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "SIGNED_QUANTITY", nullable = false, precision = 19, scale = 3)
    private BigDecimal signedQuantity;

    @Column(name = "MOVEMENT_TYPE", nullable = false, length = 30)
    private String movementType;

    @Column(name = "SEQUENCE_NO", nullable = false)
    private Integer sequenceNo;

    public MovementType getMovementType() {
        return movementType == null ? null : MovementType.fromId(movementType);
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType == null ? null : movementType.getId();
    }

    public WarehouseTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(WarehouseTransaction transaction) {
        this.transaction = transaction;
    }

    public TransactionItem getItem() {
        return item;
    }

    public void setItem(TransactionItem item) {
        this.item = item;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public BigDecimal getSignedQuantity() {
        return signedQuantity;
    }

    public void setSignedQuantity(BigDecimal signedQuantity) {
        this.signedQuantity = signedQuantity;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }
}

