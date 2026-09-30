package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@JmixEntity
@Table(name = "STOCKTAKE_ITEM", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_STOCKTAKE_ITEM_UNQ_PRODUCT", columnNames = {"STOCKTAKE_ID", "PRODUCT_ID"})
})
@Entity
public class StocktakeItem extends BaseUuidEntity {

    @JoinColumn(name = "STOCKTAKE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Stocktake stocktake;

    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @Column(name = "BOOK_QTY", nullable = false, precision = 19, scale = 3)
    private BigDecimal bookQuantity = BigDecimal.ZERO;

    @Column(name = "COUNTED_QTY", precision = 19, scale = 3)
    private BigDecimal countedQuantity;

    @Column(name = "VARIANCE_QTY", precision = 19, scale = 3)
    private BigDecimal varianceQuantity;

    public Stocktake getStocktake() {
        return stocktake;
    }

    public void setStocktake(Stocktake stocktake) {
        this.stocktake = stocktake;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getBookQuantity() {
        return bookQuantity;
    }

    public void setBookQuantity(BigDecimal bookQuantity) {
        this.bookQuantity = bookQuantity;
        recalculateVariance();
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public void setCountedQuantity(BigDecimal countedQuantity) {
        this.countedQuantity = countedQuantity;
        recalculateVariance();
    }

    public BigDecimal getVarianceQuantity() {
        return varianceQuantity;
    }

    public void setVarianceQuantity(BigDecimal varianceQuantity) {
        this.varianceQuantity = varianceQuantity;
    }

    private void recalculateVariance() {
        if (bookQuantity != null && countedQuantity != null) {
            varianceQuantity = countedQuantity.subtract(bookQuantity);
        }
    }
}
