package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.TransactionItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Normalizes transaction lines so an identical product in the same unit appears once.
 * Different units are deliberately not merged: this project does not implement unit conversion.
 */
public final class TransactionItemMergeSupport {

    private TransactionItemMergeSupport() {
    }

    public static boolean mergeDuplicateProducts(List<TransactionItem> items) {
        if (items == null || items.size() < 2) {
            renumber(items);
            return false;
        }

        Map<ProductUnitKey, TransactionItem> firstItemByProductAndUnit = new HashMap<>();
        List<Integer> duplicateIndexes = new ArrayList<>();
        boolean merged = false;

        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            if (item == null || item.getProduct() == null || item.getProduct().getId() == null
                    || item.getUnit() == null || item.getUnit().getId() == null
                    || item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                continue;
            }

            ProductUnitKey key = new ProductUnitKey(
                    item.getProduct().getId(), item.getUnit().getId());
            TransactionItem existing = firstItemByProductAndUnit.putIfAbsent(key, item);
            if (existing == null) {
                continue;
            }

            BigDecimal existingQuantity = existing.getQuantity();
            if (existingQuantity == null || existingQuantity.signum() <= 0) {
                continue;
            }

            existing.setQuantity(existingQuantity.add(item.getQuantity()));
            if ((existing.getNote() == null || existing.getNote().isBlank())
                    && item.getNote() != null && !item.getNote().isBlank()) {
                existing.setNote(item.getNote());
            }
            duplicateIndexes.add(index);
            merged = true;
        }

        // Jmix uses ObservableList here; remove from highest index to lowest.
        for (int index = duplicateIndexes.size() - 1; index >= 0; index--) {
            items.remove((int) duplicateIndexes.get(index));
        }

        renumber(items);
        return merged;
    }

    private static void renumber(List<TransactionItem> items) {
        if (items == null) {
            return;
        }
        int lineNo = 1;
        for (TransactionItem item : items) {
            if (item != null) {
                item.setLineNo(lineNo++);
            }
        }
    }

    private record ProductUnitKey(UUID productId, UUID unitId) {
    }
}
