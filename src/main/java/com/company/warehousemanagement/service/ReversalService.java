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
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ReversalService {

    private final DataManager dataManager;
    private final CurrentAuthentication currentAuthentication;
    private final AvailableStockService availableStockService;

    public ReversalService(DataManager dataManager,
                           CurrentAuthentication currentAuthentication,
                           AvailableStockService availableStockService) {
        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
        this.availableStockService = availableStockService;
    }

    @Transactional
    public WarehouseTransaction reverse(UUID transactionId) {

        if (transactionId == null) {
            throw new WarehouseBusinessException(
                    "Không thể reverse phiếu chưa được lưu"
            );
        }

        if (!currentAuthentication.isSet()) {
            throw new WarehouseBusinessException(
                    "Không xác định được người dùng hiện tại"
            );
        }


        WarehouseTransaction original = loadTransactionForUpdate(transactionId);

        if (original == null) {
            throw new WarehouseBusinessException(
                    "Không tìm thấy phiếu cần reverse"
            );
        }

        if (original.getReversalOf() != null) {
            throw new WarehouseBusinessException(
                    "Không thể reverse một phiếu reversal"
            );
        }


        if (original.getStatus()
                == WarehouseTransactionStatus.REVERSED) {

            WarehouseTransaction existingReversal =
                    findExistingReversal(original.getId());

            if (existingReversal != null) {
                return existingReversal;
            }

            throw new WarehouseBusinessException(
                    "Phiếu đã ở trạng thái REVERSED nhưng không tìm thấy phiếu reversal tương ứng"
            );
        }

        if (original.getStatus()
                != WarehouseTransactionStatus.POSTED) {

            throw new WarehouseBusinessException(
                    "Chỉ phiếu POSTED mới được phép reverse"
            );
        }

        if (original.getType() != WarehouseTransactionType.EXPORT
                && original.getType() != WarehouseTransactionType.TRANSFER) {

            throw new WarehouseBusinessException(
                    "O06 chưa hỗ trợ reverse loại chứng từ: "
                            + original.getType()
            );
        }

        validateOriginal(original);

        OffsetDateTime reversedAt = OffsetDateTime.now();

        /*
         * Tạo transaction reversal trước.
         *
         * Transaction này sẽ:
         * - POSTED ngay
         * - reversalOf = original
         */
        WarehouseTransaction reversal =
                createReversalTransaction(original, reversedAt);

        /*
         * Sort product theo UUID để thứ tự lock ổn định.
         */
        List<TransactionItem> items =
                new ArrayList<>(original.getItems());

        items.sort(
                Comparator.comparing(
                        item -> item.getProduct().getId()
                )
        );

        int sequenceNo = 1;

        for (TransactionItem originalItem : items) {

            Product product = originalItem.getProduct();

            BigDecimal quantity = originalItem.getQuantity();

            if (quantity == null
                    || quantity.signum() <= 0) {

                throw new WarehouseBusinessException(
                        "Dòng " + originalItem.getLineNo()
                                + ": số lượng không hợp lệ"
                );
            }

            /*
             * Tạo item cho phiếu reversal.
             */
            TransactionItem reversalItem =
                    dataManager.create(TransactionItem.class);

            reversalItem.setLineNo(originalItem.getLineNo());
            reversalItem.setProduct(product);
            reversalItem.setQuantity(quantity);
            reversalItem.setUnit(originalItem.getUnit());
            reversalItem.setNote(
                    "Reversal of line "
                            + originalItem.getLineNo()
            );

            reversal.addItem(reversalItem);
        }

        /*
         * Save transaction reversal + composition items.
         */
        reversal = dataManager.save(reversal);

        /*
         * Xử lý inventory.
         */
        for (TransactionItem reversalItem : reversal.getItems()) {

            Product product = reversalItem.getProduct();

            BigDecimal quantity = reversalItem.getQuantity();

            if (original.getType()
                    == WarehouseTransactionType.EXPORT) {

                /*
                 * EXPORT:
                 *
                 * POST:
                 *   Source - quantity
                 *
                 * REVERSE:
                 *   Source + quantity
                 */
                Inventory sourceInventory =
                        availableStockService.loadAndLockInventory(
                                original.getSourceWarehouse().getId(),
                                product.getId()
                        );

                BigDecimal currentQuantity =
                        getQuantity(sourceInventory);

                sourceInventory.setQuantity(
                        currentQuantity.add(quantity)
                );

                sourceInventory.setUpdatedAt(reversedAt);

                dataManager.save(sourceInventory);

                /*
                 * Ledger reversal: +quantity
                 */
                InventoryMovement movement =
                        createReversalMovement(
                                reversal,
                                reversalItem,
                                original.getSourceWarehouse(),
                                product,
                                quantity,
                                sequenceNo++,
                                reversedAt
                        );

                dataManager.save(movement);

            } else {

                /*
                 * TRANSFER:
                 *
                 * POST:
                 *   Source - quantity
                 *   Destination + quantity
                 *
                 * REVERSE:
                 *   Source + quantity
                 *   Destination - quantity
                 */

                UUID sourceId =
                        original.getSourceWarehouse().getId();

                UUID destinationId =
                        original.getDestinationWarehouse().getId();

                /*
                 * Khóa theo thứ tự UUID ổn định.
                 * Điều này giúp giảm nguy cơ deadlock
                 * khi có các request đồng thời.
                 */
                Inventory first;
                Inventory second;

                if (sourceId.compareTo(destinationId) < 0) {

                    first =
                            availableStockService.loadAndLockInventory(
                                    sourceId,
                                    product.getId()
                            );

                    second =
                            availableStockService.loadAndLockInventory(
                                    destinationId,
                                    product.getId()
                            );

                } else {

                    first =
                            availableStockService.loadAndLockInventory(
                                    destinationId,
                                    product.getId()
                            );

                    second =
                            availableStockService.loadAndLockInventory(
                                    sourceId,
                                    product.getId()
                            );
                }

                Inventory sourceInventory =
                        first.getWarehouse().getId().equals(sourceId)
                                ? first
                                : second;

                Inventory destinationInventory =
                        first.getWarehouse().getId().equals(destinationId)
                                ? first
                                : second;

                /*
                 * Kiểm tra destination có đủ để trả lại không.
                 *
                 * Ví dụ:
                 * Transfer ban đầu +30 vào destination.
                 *
                 * Nếu hiện destination chỉ còn 20
                 * thì không thể reverse vì sẽ tạo tồn âm.
                 */
                BigDecimal destinationQuantity =
                        getQuantity(destinationInventory);

                if (destinationQuantity.compareTo(quantity) < 0) {

                    throw new WarehouseBusinessException(
                            "Không thể reverse phiếu transfer cho sản phẩm '"
                                    + getProductDisplayName(product)
                                    + "'. "
                                    + "Tồn kho đích hiện tại: "
                                    + destinationQuantity
                                    + ", cần hoàn trả: "
                                    + quantity
                    );
                }

                BigDecimal sourceQuantity =
                        getQuantity(sourceInventory);

                sourceInventory.setQuantity(
                        sourceQuantity.add(quantity)
                );

                destinationInventory.setQuantity(
                        destinationQuantity.subtract(quantity)
                );

                sourceInventory.setUpdatedAt(reversedAt);
                destinationInventory.setUpdatedAt(reversedAt);

                dataManager.save(sourceInventory);
                dataManager.save(destinationInventory);

                /*
                 * REVERSAL tại source: +quantity
                 */
                InventoryMovement sourceMovement =
                        createReversalMovement(
                                reversal,
                                reversalItem,
                                original.getSourceWarehouse(),
                                product,
                                quantity,
                                sequenceNo++,
                                reversedAt
                        );

                dataManager.save(sourceMovement);

                /*
                 * REVERSAL tại destination: -quantity
                 */
                InventoryMovement destinationMovement =
                        createReversalMovement(
                                reversal,
                                reversalItem,
                                original.getDestinationWarehouse(),
                                product,
                                quantity.negate(),
                                sequenceNo++,
                                reversedAt
                        );

                dataManager.save(destinationMovement);
            }
        }

        /*
         * Chỉ chuyển original -> REVERSED
         * sau khi toàn bộ inventory + movement
         * đã xử lý xong.
         */
        original.setStatus(
                WarehouseTransactionStatus.REVERSED
        );

        dataManager.save(original);

        return reversal;
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

                                .add("sourceWarehouse",
                                        FetchPlan.BASE)

                                .add("destinationWarehouse",
                                        FetchPlan.BASE)

                                .add("partner",
                                        FetchPlan.BASE)

                                .add("items", itemFp ->
                                        itemFp
                                                .addFetchPlan(FetchPlan.BASE)
                                                .add("lineNo")
                                                .add("quantity")
                                                .add("product",
                                                        FetchPlan.BASE)
                                                .add("unit",
                                                        FetchPlan.BASE)
                                )
                        )
                        .lockMode(
                                LockModeType.PESSIMISTIC_WRITE
                        )
                        .list();

        if (transactions.isEmpty()) {
            return null;
        }

        if (transactions.size() > 1) {
            throw new WarehouseBusinessException(
                    "Dữ liệu không hợp lệ: nhiều transaction cùng ID"
            );
        }

        return transactions.get(0);
    }

    private WarehouseTransaction findExistingReversal(
            UUID originalId) {

        List<WarehouseTransaction> reversals =
                dataManager.load(WarehouseTransaction.class)
                        .query("""
                                select e
                                from WarehouseTransaction e
                                where e.reversalOf.id = ?1
                                order by e.postedAt desc
                                """,
                                originalId)
                        .fetchPlan(fpb -> fpb
                                .addFetchPlan(FetchPlan.BASE)
                        )
                        .list();

        if (reversals.isEmpty()) {
            return null;
        }

        return reversals.get(0);
    }

    private void validateOriginal(
            WarehouseTransaction original) {

        if (original.getItems() == null
                || original.getItems().isEmpty()) {

            throw new WarehouseBusinessException(
                    "Phiếu cần reverse không có sản phẩm"
            );
        }

        if (original.getSourceWarehouse() == null
                || original.getSourceWarehouse().getId() == null) {

            throw new WarehouseBusinessException(
                    "Phiếu không có kho nguồn"
            );
        }

        if (!isWarehouseActive(
                original.getSourceWarehouse().getId())) {

            throw new WarehouseBusinessException(
                    "Kho nguồn đang ngừng hoạt động"
            );
        }

        if (original.getType()
                == WarehouseTransactionType.TRANSFER) {

            if (original.getDestinationWarehouse() == null
                    || original.getDestinationWarehouse().getId() == null) {

                throw new WarehouseBusinessException(
                        "Phiếu transfer không có kho đích"
                );
            }

            if (original.getSourceWarehouse().getId()
                    .equals(
                            original.getDestinationWarehouse().getId()
                    )) {

                throw new WarehouseBusinessException(
                        "Kho nguồn và kho đích không được giống nhau"
                );
            }

            if (!isWarehouseActive(
                    original.getDestinationWarehouse().getId())) {

                throw new WarehouseBusinessException(
                        "Kho đích đang ngừng hoạt động"
                );
            }
        }

        for (TransactionItem item : original.getItems()) {

            if (item == null
                    || item.getProduct() == null
                    || item.getProduct().getId() == null) {

                throw new WarehouseBusinessException(
                        "Phiếu chứa dòng sản phẩm không hợp lệ"
                );
            }

            if (item.getQuantity() == null
                    || item.getQuantity().signum() <= 0) {

                throw new WarehouseBusinessException(
                        "Dòng "
                                + item.getLineNo()
                                + ": số lượng phải lớn hơn 0"
                );
            }
        }
    }

    private boolean isWarehouseActive(UUID warehouseId) {

        return dataManager.load(Warehouse.class)
                .id(warehouseId)
                .optional()
                .map(warehouse ->
                        Boolean.TRUE.equals(
                                warehouse.getActive()
                        ))
                .orElse(false);
    }

    private WarehouseTransaction createReversalTransaction(
            WarehouseTransaction original,
            OffsetDateTime reversedAt) {

        WarehouseTransaction reversal =
                dataManager.create(
                        WarehouseTransaction.class
                );

        reversal.setDocumentNo(
                "REV-" + UUID.randomUUID()
        );

        reversal.setType(
                original.getType()
        );

        /*
         * Phiếu reversal được POST ngay
         * vì nó đã thực hiện nghiệp vụ.
         */
        reversal.setStatus(
                WarehouseTransactionStatus.POSTED
        );

        reversal.setSourceWarehouse(
                original.getSourceWarehouse()
        );

        reversal.setDestinationWarehouse(
                original.getDestinationWarehouse()
        );

        reversal.setPartner(
                original.getPartner()
        );

        reversal.setDocumentDate(
                LocalDate.now()
        );

        reversal.setPostedAt(
                reversedAt
        );

        reversal.setPostedBy(
                currentAuthentication.getUser().getUsername()
        );

        reversal.setReversalOf(original);

        reversal.setReason(
                "Reversal of " + original.getDocumentNo()
        );

        return reversal;
    }

    private InventoryMovement createReversalMovement(
            WarehouseTransaction reversal,
            TransactionItem reversalItem,
            Warehouse warehouse,
            Product product,
            BigDecimal signedQuantity,
            int sequenceNo,
            OffsetDateTime occurredAt) {

        InventoryMovement movement =
                dataManager.create(
                        InventoryMovement.class
                );

        movement.setTransaction(
                reversal
        );

        movement.setItem(
                reversalItem
        );

        movement.setWarehouse(
                warehouse
        );

        movement.setProduct(
                product
        );

        movement.setOccurredAt(
                occurredAt
        );

        movement.setSignedQuantity(
                signedQuantity
        );

        movement.setMovementType(
                MovementType.REVERSAL
        );

        movement.setSequenceNo(
                sequenceNo
        );

        return movement;
    }

    private BigDecimal getQuantity(
            Inventory inventory) {

        if (inventory.getQuantity() == null) {
            return BigDecimal.ZERO;
        }

        return inventory.getQuantity();
    }

    private String getProductDisplayName(
            Product product) {

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