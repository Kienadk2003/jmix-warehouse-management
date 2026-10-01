package com.company.warehousemanagement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class UpdateImportReceiptItemCommand {

    private UUID itemId;
    private BigDecimal quantity;
    private String note;

    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
