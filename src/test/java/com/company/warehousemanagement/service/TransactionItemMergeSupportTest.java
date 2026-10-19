package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.TransactionItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionItemMergeSupportTest {

    @Test
    void mergesDuplicateProductQuantityAndRenumbersRemainingLines() {
        Product productA = product();
        Product productB = product();
        TransactionItem firstA = item(productA, "2.500", 1, null);
        TransactionItem productBItem = item(productB, "4", 2, "Giữ nguyên");
        TransactionItem secondA = item(productA, "3.250", 3, "Ghi chú bổ sung");
        List<TransactionItem> items = new ArrayList<>(List.of(firstA, productBItem, secondA));

        boolean merged = TransactionItemMergeSupport.mergeDuplicateProducts(items);

        assertThat(merged).isTrue();
        assertThat(items).containsExactly(firstA, productBItem);
        assertThat(firstA.getQuantity()).isEqualByComparingTo("5.750");
        assertThat(firstA.getNote()).isEqualTo("Ghi chú bổ sung");
        assertThat(firstA.getLineNo()).isEqualTo(1);
        assertThat(productBItem.getLineNo()).isEqualTo(2);
    }

    private Product product() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        return product;
    }

    private TransactionItem item(Product product, String quantity, int lineNo, String note) {
        TransactionItem item = new TransactionItem();
        item.setProduct(product);
        item.setQuantity(new BigDecimal(quantity));
        item.setLineNo(lineNo);
        item.setNote(note);
        return item;
    }
}
