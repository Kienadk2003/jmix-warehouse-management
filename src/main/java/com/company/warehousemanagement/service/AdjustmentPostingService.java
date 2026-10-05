package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.AdjustmentDirection;
import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.MovementType;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AdjustmentPostingService {

    private final DataManager dataManager;
    private final CurrentAuthentication currentAuthentication;

    public AdjustmentPostingService(
            DataManager dataManager,
            CurrentAuthentication currentAuthentication) {

        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
    }

    @Transactional
    public WarehouseTransaction postAdjustment(UUID transactionId) {

        WarehouseTransaction transaction =
                dataManager.load(WarehouseTransaction.class)
                        .query("""
                                select e
                                from WarehouseTransaction e
                                where e.id = ?1
                                """, transactionId)
                        .lockMode(LockModeType.PESSIMISTIC_WRITE)
                        .one();

        /*
         * =========================
         * STATE VALIDATION
         * =========================
         */

        if (transaction.getType()
                != WarehouseTransactionType.ADJUSTMENT) {

            throw new WarehouseBusinessException(
                    "Đây không phải phiếu ADJUSTMENT"
            );
        }

        if (transaction.getStatus()
                != WarehouseTransactionStatus.DRAFT) {

            throw new WarehouseBusinessException(
                    "Chỉ phiếu DRAFT mới được POST"
            );
        }

        if (transaction.getSourceWarehouse() == null) {

            throw new WarehouseBusinessException(
                    "Adjustment chưa có Warehouse"
            );
        }

        if (transaction.getAdjustmentDirection() == null) {

            throw new WarehouseBusinessException(
                    "Adjustment Direction phải là IN hoặc OUT"
            );
        }

        if (transaction.getReason() == null
                || transaction.getReason().isBlank()) {

            throw new WarehouseBusinessException(
                    "Adjustment bắt buộc phải có Reason"
            );
        }

        List<TransactionItem> items =
                transaction.getItems();

        if (items == null || items.isEmpty()) {

            throw new WarehouseBusinessException(
                    "Adjustment phải có ít nhất một sản phẩm"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        String username =
                currentAuthentication
                        .getUser()
                        .getUsername();

        int sequenceNo = 1;

        /*
         * =========================
         * POST EACH ITEM
         * =========================
         */

        for (TransactionItem item : items) {

            if (item == null
                    || item.getProduct() == null) {

                throw new WarehouseBusinessException(
                        "Product không được để trống"
                );
            }

            BigDecimal quantity = item.getQuantity();

            if (quantity == null
                    || quantity.signum() <= 0) {

                throw new WarehouseBusinessException(
                        "Quantity phải lớn hơn 0"
                );
            }

            UUID warehouseId =
                    transaction
                            .getSourceWarehouse()
                            .getId();

            UUID productId =
                    item.getProduct()
                            .getId();

            /*
             * Lock Inventory row.
             *
             * Nếu chưa có row:
             * - Adjustment IN  -> tạo row mới
             * - Adjustment OUT -> báo lỗi
             */

            Inventory inventory =
                    loadInventoryForAdjustment(
                            warehouseId,
                            productId
                    );

            BigDecimal currentQuantity =
                    inventory.getQuantity() == null
                            ? BigDecimal.ZERO
                            : inventory.getQuantity();

            BigDecimal signedQuantity;

            MovementType movementType;

            /*
             * =========================
             * ADJUSTMENT IN
             * =========================
             */

            if (transaction.getAdjustmentDirection()
                    == AdjustmentDirection.IN) {

                signedQuantity = quantity;

                movementType =
                        MovementType.ADJUSTMENT_IN;

            }

            /*
             * =========================
             * ADJUSTMENT OUT
             * =========================
             */

            else {

                if (currentQuantity.compareTo(quantity) < 0) {

                    throw new WarehouseBusinessException(
                            "Không thể Adjustment OUT vì tồn kho không đủ. "
                                    + "Tồn hiện tại: "
                                    + currentQuantity
                                    + ", yêu cầu: "
                                    + quantity
                    );
                }

                signedQuantity =
                        quantity.negate();

                movementType =
                        MovementType.ADJUSTMENT_OUT;
            }

            /*
             * =========================
             * UPDATE INVENTORY
             * =========================
             */

            inventory.setQuantity(
                    currentQuantity.add(signedQuantity)
            );

            inventory.setUpdatedAt(now);

            dataManager.save(inventory);

            /*
             * =========================
             * CREATE MOVEMENT
             * =========================
             */

            InventoryMovement movement =
                    dataManager.create(
                            InventoryMovement.class
                    );

            movement.setTransaction(transaction);
            movement.setItem(item);
            movement.setWarehouse(
                    transaction.getSourceWarehouse()
            );
            movement.setProduct(
                    item.getProduct()
            );
            movement.setOccurredAt(now);
            movement.setSignedQuantity(
                    signedQuantity
            );
            movement.setMovementType(
                    movementType
            );
            movement.setSequenceNo(
                    sequenceNo++
            );

            dataManager.save(movement);
        }

        /*
         * =========================
         * SET POSTED
         * =========================
         */

        transaction.setStatus(
                WarehouseTransactionStatus.POSTED
        );

        transaction.setPostedAt(now);
        transaction.setPostedBy(username);

        return dataManager.save(transaction);
    }

    private Inventory loadInventoryForAdjustment(
            UUID warehouseId,
            UUID productId) {

        List<Inventory> inventories =
                dataManager.load(Inventory.class)
                        .query("""
                                select e
                                from Inventory e
                                where e.warehouse.id = ?1
                                  and e.product.id = ?2
                                """,
                                warehouseId,
                                productId)
                        .lockMode(
                                LockModeType.PESSIMISTIC_WRITE
                        )
                        .list();

        if (inventories.size() > 1) {

            throw new WarehouseBusinessException(
                    "Dữ liệu tồn kho không hợp lệ: "
                            + "một Warehouse/Product có nhiều dòng Inventory"
            );
        }

        /*
         * Không có Inventory.
         *
         * Cho phép Adjustment IN tạo
         * dòng Inventory mới.
         */

        if (inventories.isEmpty()) {

            Inventory inventory =
                    dataManager.create(
                            Inventory.class
                    );

            inventory.setWarehouse(
                    dataManager.load(
                            com.company.warehousemanagement.entity.Warehouse.class
                    ).id(warehouseId).one()
            );

            inventory.setProduct(
                    dataManager.load(
                            com.company.warehousemanagement.entity.Product.class
                    ).id(productId).one()
            );

            inventory.setQuantity(
                    BigDecimal.ZERO
            );

            inventory.setReservedQuantity(
                    BigDecimal.ZERO
            );

            inventory.setUpdatedAt(
                    OffsetDateTime.now()
            );

            return inventory;
        }

        return inventories.get(0);
    }
}