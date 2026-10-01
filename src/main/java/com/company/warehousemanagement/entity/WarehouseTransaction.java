package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JmixEntity
@Table(name = "WAREHOUSE_TRANSACTION", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_WAREHOUSE_TRANSACTION_UNQ_NO", columnNames = "DOCUMENT_NO")
}, indexes = {
        @Index(name = "IDX_WT_TYPE_STATUS_DATE", columnList = "TYPE, STATUS, DOCUMENT_DATE"),
        @Index(name = "IDX_WT_SOURCE_DATE", columnList = "SOURCE_WAREHOUSE_ID, DOCUMENT_DATE"),
        @Index(name = "IDX_WT_DESTINATION_DATE", columnList = "DESTINATION_WAREHOUSE_ID, DOCUMENT_DATE")
})
@Entity
public class WarehouseTransaction extends AuditedEntity {

    @InstanceName
    @Column(name = "DOCUMENT_NO", nullable = false, length = 50)
    private String documentNo;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @JoinColumn(name = "SOURCE_WAREHOUSE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Warehouse sourceWarehouse;

    @JoinColumn(name = "DESTINATION_WAREHOUSE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Warehouse destinationWarehouse;

    @JoinColumn(name = "PARTNER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Partner partner;

    @Column(name = "DOCUMENT_DATE", nullable = false)
    private LocalDate documentDate;

    @Column(name = "POSTED_AT")
    private OffsetDateTime postedAt;

    @Column(name = "POSTED_BY")
    private String postedBy;

    @JoinColumn(name = "REVERSAL_OF_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private WarehouseTransaction reversalOf;

    @Column(name = "REASON", length = 1000)
    private String reason;

    public WarehouseTransactionType getType() {
        return type == null ? null : WarehouseTransactionType.fromId(type);
    }

    public void setType(WarehouseTransactionType type) {
        this.type = type == null ? null : type.getId();
    }

    public WarehouseTransactionStatus getStatus() {
        return status == null ? null : WarehouseTransactionStatus.fromId(status);
    }

    public void setStatus(WarehouseTransactionStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public Warehouse getSourceWarehouse() {
        return sourceWarehouse;
    }

    public void setSourceWarehouse(Warehouse sourceWarehouse) {
        this.sourceWarehouse = sourceWarehouse;
    }

    public Warehouse getDestinationWarehouse() {
        return destinationWarehouse;
    }

    public void setDestinationWarehouse(Warehouse destinationWarehouse) {
        this.destinationWarehouse = destinationWarehouse;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate documentDate) {
        this.documentDate = documentDate;
    }

    public OffsetDateTime getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(OffsetDateTime postedAt) {
        this.postedAt = postedAt;
    }

    public String getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(String postedBy) {
        this.postedBy = postedBy;
    }

    public WarehouseTransaction getReversalOf() {
        return reversalOf;
    }

    public void setReversalOf(WarehouseTransaction reversalOf) {
        this.reversalOf = reversalOf;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Composition
    @OneToMany(
            mappedBy = "transaction",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("lineNo")
    private List<TransactionItem> items = new ArrayList<>();

    public List<TransactionItem> getItems() {
        return items;
    }

    public void setItems(List<TransactionItem> items) {
        this.items = items;
    }
}

