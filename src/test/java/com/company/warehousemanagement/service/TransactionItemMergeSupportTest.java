package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionItemMergeSupportTest {

    @Test
    void mergesDuplicateProductAndUnitQuantityAndRenumbersRemainingLines() {
        Product productA = product();
        Product productB = product();
        Unit unit = unit();
        TransactionItem firstA = item(productA, unit, "2.500", 1, null);
        TransactionItem productBItem = item(productB, unit, "4", 2, "Giữ nguyên");
        TransactionItem secondA = item(productA, unit, "3.250", 3, "Ghi chú bổ sung");
        List<TransactionItem> items = new ArrayList<>(List.of(firstA, productBItem, secondA));

        boolean merged = TransactionItemMergeSupport.mergeDuplicateProducts(items);

        assertThat(merged).isTrue();
        assertThat(items).containsExactly(firstA, productBItem);
        assertThat(firstA.getQuantity()).isEqualByComparingTo("5.750");
        assertThat(firstA.getNote()).isEqualTo("Ghi chú bổ sung");
        assertThat(firstA.getLineNo()).isEqualTo(1);
        assertThat(productBItem.getLineNo()).isEqualTo(2);
    }

    @Test
    void doesNotMergeSameProductWhenUnitsDiffer() {
        Product product = product();
        TransactionItem pieces = item(product, unit(), "2", 1, null);
        TransactionItem boxes = item(product, unit(), "3", 2, null);
        List<TransactionItem> items = new ArrayList<>(List.of(pieces, boxes));

        boolean merged = TransactionItemMergeSupport.mergeDuplicateProducts(items);

        assertThat(merged).isFalse();
        assertThat(items).containsExactly(pieces, boxes);
        assertThat(pieces.getQuantity()).isEqualByComparingTo("2");
        assertThat(boxes.getQuantity()).isEqualByComparingTo("3");
    }

    private Product product() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        return product;
    }

    private Unit unit() {
        Unit unit = new Unit();
        unit.setId(UUID.randomUUID());
        return unit;
    }

    private TransactionItem item(Product product, Unit unit, String quantity, int lineNo, String note) {
        TransactionItem item = new TransactionItem();
        item.setProduct(product);
        item.setUnit(unit);
        item.setQuantity(new BigDecimal(quantity));
        item.setLineNo(lineNo);
        item.setNote(note);
        return item;
    }
}
