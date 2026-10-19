package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.WarehouseTransaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consolidates repeated product lines in a draft transaction.
 *
 * Inventory quantities are stored in the product's base unit. Therefore, lines
 * are merged only when both product and unit IDs match. Lines with missing
 * product/unit/quantity are deliberately left for the regular validators to
 * report instead of silently hiding invalid input.
 */
public final class TransactionItemMergeUtils {

    private TransactionItemMergeUtils() {
    }

    /**
     * Merge duplicate product lines in the transaction's in-memory item list.
     * The first valid line is retained; subsequent matching quantities are
     * added to it and the duplicate composition rows are removed.
     *
     * @return true if at least one line was merged
     */
    public static boolean mergeDuplicateProducts(WarehouseTransaction transaction) {
        if (transaction == null || transaction.getItems() == null
                || transaction.getItems().size() < 2) {
            return false;
        }

        Map<ProductUnitKey, TransactionItem> firstByProductAndUnit = new HashMap<>();
        List<TransactionItem> duplicates = new ArrayList<>();

        // Work on a copy because duplicates are removed after all sums are computed.
        for (TransactionItem item : new ArrayList<>(transaction.getItems())) {
            if (item == null || item.getProduct() == null
                    || item.getProduct().getId() == null
                    || item.getUnit() == null || item.getUnit().getId() == null
                    || item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                continue;
            }

            ProductUnitKey key = new ProductUnitKey(
                    item.getProduct().getId(), item.getUnit().getId());
            TransactionItem first = firstByProductAndUnit.putIfAbsent(key, item);
            if (first == null) {
                continue;
            }

            BigDecimal firstQuantity = first.getQuantity();
            if (firstQuantity == null) {
                // Leave invalid data visible for the normal quantity validator.
                continue;
            }

            first.setQuantity(firstQuantity.add(item.getQuantity()));
            if ((first.getNote() == null || first.getNote().isBlank())
                    && item.getNote() != null && !item.getNote().isBlank()) {
                first.setNote(item.getNote());
            }
            duplicates.add(item);
        }

        if (duplicates.isEmpty()) {
            return false;
        }

        for (TransactionItem duplicate : duplicates) {
            transaction.removeItem(duplicate);
        }

        int lineNo = 1;
        for (TransactionItem item : transaction.getItems()) {
            if (item != null) {
                item.setTransaction(transaction);
                item.setLineNo(lineNo++);
            }
        }
        return true;
    }

    private record ProductUnitKey(UUID productId, UUID unitId) {
    }
}
