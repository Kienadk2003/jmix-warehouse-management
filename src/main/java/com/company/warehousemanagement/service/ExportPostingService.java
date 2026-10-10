package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.MovementType;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.company.warehousemanagement.event.WarehouseTransactionPostedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Service
public class ExportPostingService {

    private final DataManager dataManager;
    private final CurrentAuthentication currentAuthentication;
    private final AvailableStockService availableStockService;
    private final ApplicationEventPublisher eventPublisher;
    private final WarehouseAuthorizationService authorizationService;

    public ExportPostingService(
            DataManager dataManager,
            CurrentAuthentication currentAuthentication,
            AvailableStockService availableStockService,
            ApplicationEventPublisher eventPublisher,
            WarehouseAuthorizationService authorizationService) {

        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
        this.availableStockService = availableStockService;
        this.eventPublisher = eventPublisher;
        this.authorizationService = authorizationService;
    }


    @Transactional
    public WarehouseTransaction postExport(UUID transactionId) {
        authorizationService.require(
                WarehousePermissions.POST,
                "Bạn không có quyền POST phiếu xuất"
        );

        if (transactionId == null) {
            throw new WarehouseBusinessException(
                    "Không thể POST phiếu xuất chưa được lưu"
            );
        }

        if (!currentAuthentication.isSet()) {
            throw new WarehouseBusinessException(
                    "Không xác định được người dùng hiện tại"
            );
        }

        WarehouseTransaction transaction =
                loadTransactionForUpdate(transactionId);

        validateTransaction(transaction);

        Warehouse warehouse = transaction.getSourceWarehouse();
        OffsetDateTime postedAt = OffsetDateTime.now();

        int sequenceNo = 1;

        for (TransactionItem item : transaction.getItems()) {

            Product product = item.getProduct();

            if (product == null || product.getId() == null) {
                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": sản phẩm không hợp lệ"
                );
            }

            BigDecimal quantity = item.getQuantity();

            if (quantity == null || quantity.signum() <= 0) {
                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": số lượng phải lớn hơn 0"
                );
            }

            /*
             * Khóa pessimistic dòng Inventory.
             *
             * Nếu không có Inventory thì tồn thực tế = 0,
             * vì vậy không được phép xuất.
             */
            Inventory inventory =
                    availableStockService.loadAndLockInventory(
                            warehouse.getId(),
                            product.getId()
                    );

            availableStockService.validateLockedInventory(
                    inventory,
                    quantity,
                    product
            );

            BigDecimal currentQuantity = inventory.getQuantity();

            if (currentQuantity == null) {
                currentQuantity = BigDecimal.ZERO;
                inventory.setQuantity(BigDecimal.ZERO);
            }

            /*
             * Giảm tồn.
             */
            BigDecimal newQuantity = currentQuantity.subtract(quantity);

            inventory.setQuantity(newQuantity);
            inventory.setUpdatedAt(postedAt);

            dataManager.save(inventory);

            /*
             * Ghi ledger.
             *
             * EXPORT => signed quantity âm.
             */
            InventoryMovement movement =
                    dataManager.create(InventoryMovement.class);

            movement.setTransaction(transaction);
            movement.setItem(item);
            movement.setWarehouse(warehouse);
            movement.setProduct(product);
            movement.setOccurredAt(postedAt);
            movement.setSignedQuantity(quantity.negate());
            movement.setMovementType(MovementType.EXPORT);
            movement.setSequenceNo(sequenceNo++);

            dataManager.save(movement);
        }

        /*
         * Chỉ đổi trạng thái sau khi toàn bộ item
         * đã xử lý thành công.
         */
        transaction.setStatus(WarehouseTransactionStatus.POSTED);
        transaction.setPostedAt(postedAt);
        transaction.setPostedBy(
                currentAuthentication.getUser().getUsername()
        );

        WarehouseTransaction postedTransaction =
                dataManager.save(transaction);

        eventPublisher.publishEvent(
                new WarehouseTransactionPostedEvent(
                        postedTransaction.getId()
                )
        );

        return postedTransaction;
    }

    private WarehouseTransaction loadTransactionForUpdate(
            UUID transactionId) {

        List<WarehouseTransaction> transactions =
                dataManager.load(WarehouseTransaction.class)
                        .query("""
                            select e
                            from WarehouseTransaction e
                            where e.id = ?1
                            """,
                                transactionId)
                        .fetchPlan(fpb -> fpb
                                .addFetchPlan(FetchPlan.BASE)
                                .add("sourceWarehouse", FetchPlan.BASE)
                                .add("items", itemFp ->
                                        itemFp
                                                .addFetchPlan(FetchPlan.BASE)
                                                .add("lineNo")
                                                .add("quantity")
                                                .add("product", FetchPlan.BASE)
                                                .add("unit", FetchPlan.BASE)
                                )
                        )
                        .lockMode(
                                LockModeType.PESSIMISTIC_WRITE
                        )
                        .list();

        if (transactions.isEmpty()) {
            throw new WarehouseBusinessException(
                    "Không tìm thấy phiếu xuất"
            );
        }

        if (transactions.size() > 1) {
            throw new WarehouseBusinessException(
                    "Dữ liệu không hợp lệ: nhiều phiếu có cùng ID"
            );
        }

        return transactions.get(0);
    }

    private void validateTransaction(
            WarehouseTransaction transaction) {

        if (transaction == null) {
            throw new WarehouseBusinessException(
                    "Không tìm thấy phiếu xuất"
            );
        }

        if (transaction.getType()
                != WarehouseTransactionType.EXPORT) {

            throw new WarehouseBusinessException(
                    "Chứng từ này không phải phiếu xuất kho"
            );
        }

        if (transaction.getStatus()
                != WarehouseTransactionStatus.APPROVED) {

            if (transaction.getStatus()
                    == WarehouseTransactionStatus.POSTED) {

                throw new WarehouseBusinessException(
                        "Phiếu xuất đã được POSTED, không thể POST lần nữa"
                );
            }

            throw new WarehouseBusinessException(
                    "Chỉ phiếu APPROVED mới được phép POST"
            );
        }

        if (transaction.getSourceWarehouse() == null
                || transaction.getSourceWarehouse().getId() == null) {

            throw new WarehouseBusinessException(
                    "Phiếu xuất chưa có kho xuất"
            );
        }

        Warehouse warehouse = dataManager.load(Warehouse.class)
                .id(transaction.getSourceWarehouse().getId())
                .optional()
                .orElseThrow(() ->
                        new WarehouseBusinessException(
                                "Không tìm thấy kho xuất"
                        )
                );

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new WarehouseBusinessException(
                    "Kho xuất đang ngừng hoạt động"
            );
        }

        if (transaction.getItems() == null
                || transaction.getItems().isEmpty()) {

            throw new WarehouseBusinessException(
                    "Phiếu xuất phải có ít nhất 1 dòng sản phẩm"
            );
        }

        for (TransactionItem item : transaction.getItems()) {

            if (item == null) {
                throw new WarehouseBusinessException(
                        "Phiếu xuất chứa dòng dữ liệu không hợp lệ"
                );
            }

            if (item.getProduct() == null
                    || item.getProduct().getId() == null) {

                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": chưa có sản phẩm"
                );
            }

            Product product = dataManager.load(Product.class)
                    .id(item.getProduct().getId())
                    .optional()
                    .orElseThrow(() ->
                            new WarehouseBusinessException(
                                    "Dòng " + item.getLineNo()
                                            + ": không tìm thấy sản phẩm"
                            )
                    );

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": sản phẩm đang ngừng hoạt động"
                );
            }

            if (item.getQuantity() == null
                    || item.getQuantity().signum() <= 0) {

                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": số lượng phải lớn hơn 0"
                );
            }

            if (item.getUnit() == null
                    || item.getUnit().getId() == null) {

                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": chưa có đơn vị tính"
                );
            }
            ProductUnitQuantityValidator.validate(
                    dataManager, product, item.getUnit(),
                    item.getQuantity(), item.getLineNo());
        }
    }



    private String getProductDisplayName(Product product) {

        if (product == null) {
            return "không xác định";
        }

        if (product.getCode() != null
                && !product.getCode().isBlank()) {

            return product.getCode();
        }

        if (product.getName() != null
                && !product.getName().isBlank()) {

            return product.getName();
        }

        return "không xác định";
    }
}
