package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.TransactionItem;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Normalizes transaction lines so each product appears only once.
 */
public final class TransactionItemMergeSupport {

    private TransactionItemMergeSupport() {
    }

    public static boolean mergeDuplicateProducts(List<TransactionItem> items) {
        if (items == null || items.size() < 2) {
            renumber(items);
            return false;
        }

        Map<UUID, TransactionItem> firstItemByProduct = new HashMap<>();
        List<Integer> duplicateIndexes = new ArrayList<>();
        boolean merged = false;

        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            if (item == null || item.getProduct() == null || item.getProduct().getId() == null) {
                continue;
            }

            TransactionItem existing = firstItemByProduct.putIfAbsent(item.getProduct().getId(), item);
            if (existing == null) {
                continue;
            }

            if (existing.getQuantity() != null && item.getQuantity() != null) {
                existing.setQuantity(existing.getQuantity().add(item.getQuantity()));
                if ((existing.getNote() == null || existing.getNote().isBlank())
                        && item.getNote() != null && !item.getNote().isBlank()) {
                    existing.setNote(item.getNote());
                }
                duplicateIndexes.add(index);
                merged = true;
            }
        }

        // Jmix uses ObservableList here. Its iterator intentionally does not
        // support remove(). Removing by descending index also avoids calling
        // equals() on newly created Jmix entities that do not have an ID yet.
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
}
