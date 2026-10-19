package com.company.warehousemanagement.service;

import com.company.warehousemanagement.WarehouseManagementApplication;
import com.company.warehousemanagement.entity.AdjustmentDirection;
import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.PartnerType;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.ProductCategory;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.email.Emailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = WarehouseManagementApplication.class)
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
@Transactional
class StockReducingDraftValidationTest {

    @Autowired DataManager data;
    @Autowired ExportIssueService exportIssueService;
    @Autowired TransferIssueService transferIssueService;
    @Autowired AdjustmentIssueService adjustmentIssueService;
    @MockitoBean Emailer emailer;

    Warehouse source;
    Warehouse destination;
    Product product;
    Unit unit;
    Partner customer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        source = warehouse("SRC-" + suffix);
        destination = warehouse("DST-" + suffix);

        unit = data.create(Unit.class);
        unit.setCode("U-" + suffix);
        unit.setName("Cái");
        unit = data.save(unit);

        ProductCategory category = data.create(ProductCategory.class);
        category.setCode("C-" + suffix);
        category.setName("Nhóm test");
        category = data.save(category);

        product = data.create(Product.class);
        product.setCode("P-" + suffix);
        product.setName("Sản phẩm test");
        product.setCategory(category);
        product.setBaseUnit(unit);
        product = data.save(product);

        customer = data.create(Partner.class);
        customer.setCode("CUS-" + suffix);
        customer.setName("Khách hàng test");
        customer.setPartnerType(PartnerType.CUSTOMER);
        customer = data.save(customer);

        Inventory inventory = data.create(Inventory.class);
        inventory.setWarehouse(source);
        inventory.setProduct(product);
        inventory.setQuantity(new BigDecimal("5"));
        inventory.setReservedQuantity(BigDecimal.ZERO);
        inventory.setUpdatedAt(OffsetDateTime.now());
        data.save(inventory);
    }

    @Test
    void exportTransferAndAdjustmentOutRejectQuantityAboveCurrentStock() {
        WarehouseTransaction export = data.create(WarehouseTransaction.class);
        exportIssueService.prepareNewDraft(export);
        export.setSourceWarehouse(source);
        export.setPartner(customer);
        export.addItem(item("6"));

        WarehouseTransaction transfer = data.create(WarehouseTransaction.class);
        transferIssueService.prepareNewDraft(transfer);
        transfer.setSourceWarehouse(source);
        transfer.setDestinationWarehouse(destination);
        transfer.addItem(item("6"));

        WarehouseTransaction adjustment = data.create(WarehouseTransaction.class);
        adjustmentIssueService.prepareNewDraft(adjustment);
        adjustment.setSourceWarehouse(source);
        adjustment.setAdjustmentDirection(AdjustmentDirection.OUT);
        adjustment.setReason("Điều chỉnh giảm test");
        adjustment.addItem(item("6"));

        assertThatThrownBy(() -> exportIssueService.validateDraft(export))
                .isInstanceOf(WarehouseBusinessException.class)
                .hasMessageContaining("tồn khả dụng: 5");
        assertThatThrownBy(() -> transferIssueService.validateDraft(transfer))
                .isInstanceOf(WarehouseBusinessException.class)
                .hasMessageContaining("tồn khả dụng: 5");
        assertThatThrownBy(() -> adjustmentIssueService.validateDraft(adjustment))
                .isInstanceOf(WarehouseBusinessException.class)
                .hasMessageContaining("tồn khả dụng: 5");

        adjustment.setAdjustmentDirection(AdjustmentDirection.IN);
        assertThatCode(() -> adjustmentIssueService.validateDraft(adjustment))
                .doesNotThrowAnyException();
    }

    private Warehouse warehouse(String code) {
        Warehouse warehouse = data.create(Warehouse.class);
        warehouse.setCode(code);
        warehouse.setName(code);
        return data.save(warehouse);
    }

    private TransactionItem item(String quantity) {
        TransactionItem item = data.create(TransactionItem.class);
        item.setProduct(product);
        item.setUnit(unit);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }
}
