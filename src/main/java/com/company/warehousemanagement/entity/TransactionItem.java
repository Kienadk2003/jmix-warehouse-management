package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@JmixEntity
@Table(name = "TRANSACTION_ITEM", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_TRANSACTION_ITEM_UNQ_LINE", columnNames = {"TRANSACTION_ID", "LINE_NO"})
}, indexes = {
        @Index(name = "IDX_TRANSACTION_ITEM_PRODUCT", columnList = "PRODUCT_ID")
})
@Entity
public class TransactionItem extends BaseUuidEntity {

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @JoinColumn(name = "TRANSACTION_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private WarehouseTransaction transaction;

    @Column(name = "LINE_NO", nullable = false)
    private Integer lineNo;

    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(name = "QUANTITY", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @JoinColumn(name = "UNIT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Unit unit;

    @Column(name = "NOTE", length = 1000)
    private String note;

    public Integer getVersion() {
        return version;
    }

    public WarehouseTransaction getTransaction() {
        return transaction;
    }

    public void setTransaction(WarehouseTransaction transaction) {
        this.transaction = transaction;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}

