package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JmixEntity
@Table(name = "STOCKTAKE", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_STOCKTAKE_UNQ_NO", columnNames = "DOCUMENT_NO")
}, indexes = {
        @Index(name = "IDX_STOCKTAKE_WAREHOUSE_STATUS_DATE", columnList = "WAREHOUSE_ID, STATUS, SNAPSHOT_AT")
})
@Entity
public class Stocktake extends AuditedEntity {

    @InstanceName
    @Column(name = "DOCUMENT_NO", nullable = false, length = 50)
    private String documentNo;

    @JoinColumn(name = "WAREHOUSE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Warehouse warehouse;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "SNAPSHOT_AT")
    private OffsetDateTime snapshotAt;

    @Column(name = "APPROVED_AT")
    private OffsetDateTime approvedAt;

    public StocktakeStatus getStatus() {
        return status == null ? null : StocktakeStatus.fromId(status);
    }

    public void setStatus(StocktakeStatus status) {
        this.status = status == null ? null : status.getId();
    }

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public OffsetDateTime getSnapshotAt() {
        return snapshotAt;
    }

    public void setSnapshotAt(OffsetDateTime snapshotAt) {
        this.snapshotAt = snapshotAt;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
    @Composition
    @OneToMany(
            mappedBy = "stocktake",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<StocktakeItem> items = new ArrayList<>();

    public List<StocktakeItem> getItems() {
        return items;
    }

    public void setItems(List<StocktakeItem> items) {
        this.items = items;
    }
}

