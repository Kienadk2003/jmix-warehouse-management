package com.company.warehousemanagement.reporting;

import com.company.warehousemanagement.entity.*;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.*;
import com.company.warehousemanagement.service.*;
import com.company.warehousemanagement.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.email.EmailInfo;
import io.jmix.email.Emailer;
import io.jmix.security.role.RoleGrantedAuthorityUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
@Transactional
class WarehouseReportingTest {
    @Autowired DataManager data;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    @Autowired WarehouseReportService reports;
    @Autowired TransactionDocumentService documents;
    @Autowired WarehouseTransactionEmailService emailNotifications;
    @Autowired RoleGrantedAuthorityUtils authorities;
    @MockitoBean Emailer emailer;
    Warehouse warehouse;
    Product product;
    Unit unit;
    String suffix;
    final LocalDate day = LocalDate.of(2026, 10, 11);
    final byte[] pdf = "%PDF-1.7\n%%EOF".getBytes();

    @BeforeEach void setup() {
        suffix = UUID.randomUUID().toString();
        warehouse = warehouse("A");
        unit = data.create(Unit.class); unit.setCode(suffix); unit.setName("Cái"); unit = data.save(unit);
        ProductCategory category = data.create(ProductCategory.class); category.setCode(suffix); category.setName("Test"); category = data.save(category);
        product = data.create(Product.class); product.setCode(suffix); product.setName("Sản phẩm thử"); product.setCategory(category); product.setBaseUnit(unit); product = data.save(product);
    }

    @Test void reportFiltersBoundariesStatusAndLedgerBalance() {
        var before = transaction(warehouse, WarehouseTransactionStatus.POSTED, day.minusDays(1));
        movement(before, "10", "2026-10-10T23:59:59+07:00");
        var current = transaction(warehouse, WarehouseTransactionStatus.POSTED, day);
        movement(current, "5", "2026-10-11T00:00:00+07:00");
        movement(current, "-2", "2026-10-11T23:59:59+07:00");
        movement(current, "99", "2026-10-12T00:00:00+07:00");
        var pending = transaction(warehouse, WarehouseTransactionStatus.PENDING_APPROVAL, day);
        line(pending);
        var stocktake = data.create(Stocktake.class);
        stocktake.setDocumentNo(UUID.randomUUID().toString()); stocktake.setWarehouse(warehouse);
        stocktake.setStatus(StocktakeStatus.PENDING_APPROVAL); stocktake.setSnapshotAt(day.atStartOfDay().atOffset(ZoneOffset.ofHours(7)));
        stocktake = data.save(stocktake);
        var counted = data.create(StocktakeItem.class); counted.setStocktake(stocktake); counted.setProduct(product);
        counted.setBookQuantity(BigDecimal.TEN); counted.setCountedQuantity(new BigDecimal("8")); data.save(counted);
        em.flush();
        var snapshot = reports.build(warehouse.getId(), "  " + product.getCode().toUpperCase(Locale.ROOT) + " ", day, day);
        assertThat(snapshot.transactions()).hasSize(2);
        assertThat(snapshot.posted(WarehouseTransactionType.IMPORT)).isEqualTo(1);
        assertThat(snapshot.pending()).isEqualTo(2);
        assertThat(snapshot.counts()).hasSize(1);
        assertThat(snapshot.counts().getFirst().getVarianceQuantity()).isEqualByComparingTo("-2");
        assertThat(snapshot.inventory()).hasSize(1);
        var row = snapshot.inventory().getFirst();
        assertThat(row.opening()).isEqualByComparingTo("10");
        assertThat(row.closing()).isEqualByComparingTo("13");
        assertThat(row.product().getBaseUnit().getName()).isEqualTo("Cái");
        assertThat(reports.build(warehouse.getId(), "unknown", day, day).transactions()).isEmpty();
        assertThatThrownBy(() -> reports.build(null, null, day, day.minusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void uploadsMultipleDocumentsAndRejectsChangesAfterSubmission() throws Exception {
        var transaction = transaction(warehouse, WarehouseTransactionStatus.DRAFT, day);
        var first = documents.upload(transaction.getId(), "one.pdf", new ByteArrayInputStream(pdf));
        documents.upload(transaction.getId(), "two.pdf", new ByteArrayInputStream(pdf));
        assertThat(documents.list(transaction.getId())).hasSize(2);
        assertThat(first.getFileSize()).isEqualTo(pdf.length);
        assertThat(first.getCreatedBy()).isEqualTo("admin");
        assertThat(documents.download(first.getId()).bytes()).containsExactly(pdf);
        transaction = data.load(WarehouseTransaction.class).id(transaction.getId()).one();
        transaction.setStatus(WarehouseTransactionStatus.PENDING_APPROVAL); data.save(transaction);
        var id = transaction.getId();
        assertThatThrownBy(() -> documents.upload(id, "late.pdf", new ByteArrayInputStream(pdf))).isInstanceOf(WarehouseBusinessException.class);
    }

    @Test void viewerCannotUploadAndOtherWarehouseIsInvisible() throws Exception {
        var own = transaction(warehouse, WarehouseTransactionStatus.DRAFT, day);
        var ownFile = documents.upload(own.getId(), "own.pdf", new ByteArrayInputStream(pdf));
        var outside = transaction(warehouse("B"), WarehouseTransactionStatus.DRAFT, day);
        var outsideFile = documents.upload(outside.getId(), "outside.pdf", new ByteArrayInputStream(pdf));
        var outsideStocktake = data.create(Stocktake.class);
        outsideStocktake.setDocumentNo(UUID.randomUUID().toString());
        outsideStocktake.setWarehouse(outside.getDestinationWarehouse());
        outsideStocktake.setStatus(StocktakeStatus.PENDING_APPROVAL);
        outsideStocktake.setSnapshotAt(day.atStartOfDay().atOffset(ZoneOffset.ofHours(7)));
        data.save(outsideStocktake);
        User user = data.create(User.class); user.setUsername("viewer-" + suffix); user = data.save(user);
        UserWarehouse assignment = data.create(UserWarehouse.class); assignment.setUser(user); assignment.setWarehouse(warehouse); data.save(assignment);
        em.flush();
        var previous = SecurityContextHolder.getContext().getAuthentication();
        var roles = List.of(authorities.createResourceRoleGrantedAuthority(ViewerRole.CODE), authorities.createRowLevelRoleGrantedAuthority(WarehouseScopeRowLevelRole.CODE));
        user.setAuthorities(roles);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "", roles));
        try {
            assertThat(documents.download(ownFile.getId()).bytes()).containsExactly(pdf);
            assertThatThrownBy(() -> documents.download(outsideFile.getId())).isInstanceOf(WarehouseBusinessException.class);
            assertThatThrownBy(() -> documents.upload(own.getId(), "denied.pdf", new ByteArrayInputStream(pdf))).isInstanceOf(WarehouseBusinessException.class);
            assertThat(reports.build(null, null, day, day).transactions()).extracting(WarehouseTransaction::getId).containsExactly(own.getId());
            assertThat(reports.build(null, null, day, day).stocktakes()).isEmpty();
        } finally { SecurityContextHolder.getContext().setAuthentication(previous); }
    }

    @Test void staffUploadsAndManagerReadsPendingDocumentsWithinAssignedWarehouse() throws Exception {
        var transaction = transaction(warehouse, WarehouseTransactionStatus.DRAFT, day);
        User user = data.create(User.class); user.setUsername("staff-" + suffix); user = data.save(user);
        UserWarehouse assignment = data.create(UserWarehouse.class); assignment.setUser(user); assignment.setWarehouse(warehouse); data.save(assignment);
        var previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            var staffRoles = List.of(authorities.createResourceRoleGrantedAuthority(WarehouseStaffRole.CODE), authorities.createRowLevelRoleGrantedAuthority(WarehouseScopeRowLevelRole.CODE));
            user.setAuthorities(staffRoles);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "", staffRoles));
            var document = documents.upload(transaction.getId(), "staff.pdf", new ByteArrayInputStream(pdf));
            assertThat(document.getCreatedBy()).isEqualTo(user.getUsername());
            transaction.setStatus(WarehouseTransactionStatus.PENDING_APPROVAL);
            data.save(transaction);
            var managerRoles = List.of(authorities.createResourceRoleGrantedAuthority(WarehouseManagerRole.CODE), authorities.createRowLevelRoleGrantedAuthority(WarehouseScopeRowLevelRole.CODE));
            user.setAuthorities(managerRoles);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "", managerRoles));
            assertThat(documents.list(transaction.getId())).hasSize(1);
            assertThat(documents.download(document.getId()).bytes()).containsExactly(pdf);
        } finally { SecurityContextHolder.getContext().setAuthentication(previous); }
    }

    @Test void postedImportAndExportShowEmailWasReallySentToPartner() throws Exception {
        Partner supplier = data.create(Partner.class);
        supplier.setCode("SUP-" + suffix);
        supplier.setName("Nhà cung cấp thử");
        supplier.setPartnerType(PartnerType.SUPPLIER);
        supplier.setEmail("supplier@example.com");
        supplier = data.save(supplier);

        WarehouseTransaction transaction = transaction(warehouse, WarehouseTransactionStatus.POSTED, day);
        transaction.setPartner(supplier);
        transaction = data.save(transaction);

        var importResult = emailNotifications.sendPostedNotification(transaction.getId());

        assertThat(importResult.sent()).isTrue();
        assertThat(importResult.message()).contains("supplier@example.com");

        Partner customer = data.create(Partner.class);
        customer.setCode("CUS-" + suffix);
        customer.setName("Khách hàng thử");
        customer.setPartnerType(PartnerType.CUSTOMER);
        customer.setEmail("customer@example.com");
        customer = data.save(customer);

        WarehouseTransaction export = transaction(warehouse, WarehouseTransactionStatus.POSTED, day);
        export.setType(WarehouseTransactionType.EXPORT);
        export.setSourceWarehouse(warehouse);
        export.setDestinationWarehouse(null);
        export.setPartner(customer);
        export = data.save(export);

        var exportResult = emailNotifications.sendPostedNotification(export.getId());

        assertThat(exportResult.sent()).isTrue();
        assertThat(exportResult.message()).contains("customer@example.com");
        var email = org.mockito.ArgumentCaptor.forClass(EmailInfo.class);
        verify(emailer, times(2)).sendEmail(email.capture());
        assertThat(email.getAllValues().get(0).getAddresses()).contains("supplier@example.com");
        assertThat(email.getAllValues().get(0).getSubject()).contains("nhập kho", transaction.getDocumentNo());
        assertThat(email.getAllValues().get(1).getAddresses()).contains("customer@example.com");
        assertThat(email.getAllValues().get(1).getSubject()).contains("xuất kho", export.getDocumentNo());
    }

    Warehouse warehouse(String prefix) {
        var w = data.create(Warehouse.class); w.setCode(prefix + suffix); w.setName(prefix); return data.save(w);
    }
    WarehouseTransaction transaction(Warehouse w, WarehouseTransactionStatus status, LocalDate date) {
        var t = data.create(WarehouseTransaction.class); t.setDocumentNo(UUID.randomUUID().toString());
        t.setType(WarehouseTransactionType.IMPORT); t.setStatus(status); t.setDocumentDate(date); t.setDestinationWarehouse(w); return data.save(t);
    }
    TransactionItem line(WarehouseTransaction transaction) {
        var i = data.create(TransactionItem.class); i.setTransaction(transaction); i.setProduct(product); i.setUnit(unit);
        i.setLineNo(Math.abs(UUID.randomUUID().hashCode())); i.setQuantity(BigDecimal.ONE); return data.save(i);
    }
    void movement(WarehouseTransaction transaction, String qty, String time) {
        var m = data.create(InventoryMovement.class); m.setTransaction(transaction); m.setItem(line(transaction));
        m.setWarehouse(warehouse); m.setProduct(product); m.setSignedQuantity(new BigDecimal(qty));
        m.setOccurredAt(OffsetDateTime.parse(time)); m.setMovementType(new BigDecimal(qty).signum() > 0 ? MovementType.IMPORT : MovementType.EXPORT);
        m.setSequenceNo(1); data.save(m);
    }
}
