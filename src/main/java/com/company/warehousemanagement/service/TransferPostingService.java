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
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TransferPostingService {

    private static final int MAX_QUANTITY_SCALE = 3;
    private static final int MAX_QUANTITY_INTEGER_DIGITS = 16;

    private final DataManager dataManager;
    private final CurrentAuthentication currentAuthentication;
    private final AvailableStockService availableStockService;

    public TransferPostingService(
            DataManager dataManager,
            CurrentAuthentication currentAuthentication,
            AvailableStockService availableStockService) {

        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
        this.availableStockService = availableStockService;
    }

    /**
     * POST một phiếu TRANSFER.
     *
     * Source warehouse:
     *      quantity -= transfer quantity
     *
     * Destination warehouse:
     *      quantity += transfer quantity
     *
     * Đồng thời tạo:
     *      TRANSFER_OUT  = -quantity
     *      TRANSFER_IN   = +quantity
     *
     * Toàn bộ thao tác nằm trong cùng một transaction.
     */
    @Transactional
    public WarehouseTransaction postTransfer(UUID transactionId) {

        if (transactionId == null) {
            throw new WarehouseBusinessException(
                    "Không thể POST phiếu chuyển kho chưa được lưu"
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

        Warehouse sourceWarehouse =
                transaction.getSourceWarehouse();

        Warehouse destinationWarehouse =
                transaction.getDestinationWarehouse();

        OffsetDateTime postedAt =
                OffsetDateTime.now();

        int sequenceNo = 1;

        /*
         * Sắp xếp item theo Product ID để mọi transaction
         * xử lý theo thứ tự ổn định.
         *
         * Điều này giúp giảm nguy cơ deadlock khi nhiều
         * transfer chạy đồng thời.
         */
        List<TransactionItem> items =
                transaction.getItems()
                        .stream()
                        .sorted((a, b) ->
                                a.getProduct()
                                        .getId()
                                        .compareTo(
                                                b.getProduct().getId()
                                        )
                        )
                        .toList();

        for (TransactionItem item : items) {

            Product product = item.getProduct();

            BigDecimal quantity =
                    item.getQuantity();

            validateItem(
                    item,
                    product,
                    quantity
            );

            /*
             * =========================
             * SOURCE INVENTORY
             * =========================
             *
             * Source bắt buộc phải có tồn.
             */
            Inventory sourceInventory =
                    availableStockService
                            .loadAndLockInventory(
                                    sourceWarehouse.getId(),
                                    product.getId()
                            );

            /*
             * O03:
             * kiểm tra available stock tại thời điểm
             * thực hiện POST.
             */
            availableStockService
                    .validateLockedInventory(
                            sourceInventory,
                            quantity,
                            product
                    );

            BigDecimal sourceQuantity =
                    sourceInventory.getQuantity();

            if (sourceQuantity == null) {
                sourceQuantity = BigDecimal.ZERO;
            }

            /*
             * =========================
             * DESTINATION INVENTORY
             * =========================
             *
             * Nếu destination chưa có Inventory,
             * tạo mới với quantity = 0 trước khi cộng.
             */
            Inventory destinationInventory =
                    loadDestinationInventory(
                            destinationWarehouse.getId(),
                            product.getId()
                    );

            if (destinationInventory == null) {

                destinationInventory =
                        dataManager.create(
                                Inventory.class
                        );

                destinationInventory.setWarehouse(
                        destinationWarehouse
                );

                destinationInventory.setProduct(
                        product
                );

                destinationInventory.setQuantity(
                        BigDecimal.ZERO
                );

                destinationInventory.setReservedQuantity(
                        BigDecimal.ZERO
                );
            }

            BigDecimal destinationQuantity =
                    destinationInventory.getQuantity();

            if (destinationQuantity == null) {
                destinationQuantity =
                        BigDecimal.ZERO;
            }

            /*
             * =========================
             * UPDATE SOURCE
             * =========================
             */

            BigDecimal newSourceQuantity =
                    sourceQuantity.subtract(quantity);

            /*
             * Đây chỉ là lớp bảo vệ cuối cùng.
             */
            if (newSourceQuantity.signum() < 0) {
                throw new WarehouseBusinessException(
                        "Không thể chuyển kho vì tồn nguồn bị âm"
                );
            }

            sourceInventory.setQuantity(
                    newSourceQuantity
            );

            sourceInventory.setUpdatedAt(
                    postedAt
            );

            /*
             * =========================
             * UPDATE DESTINATION
             * =========================
             */

            BigDecimal newDestinationQuantity =
                    destinationQuantity.add(quantity);

            destinationInventory.setQuantity(
                    newDestinationQuantity
            );

            destinationInventory.setUpdatedAt(
                    postedAt
            );

            /*
             * =========================
             * SAVE INVENTORIES
             * =========================
             */

            dataManager.save(
                    sourceInventory,
                    destinationInventory
            );

            /*
             * =========================
             * TRANSFER OUT
             * =========================
             */

            InventoryMovement transferOut =
                    dataManager.create(
                            InventoryMovement.class
                    );

            transferOut.setTransaction(
                    transaction
            );

            transferOut.setItem(
                    item
            );

            transferOut.setWarehouse(
                    sourceWarehouse
            );

            transferOut.setProduct(
                    product
            );

            transferOut.setOccurredAt(
                    postedAt
            );

            transferOut.setSignedQuantity(
                    quantity.negate()
            );

            transferOut.setMovementType(
                    MovementType.TRANSFER_OUT
            );

            transferOut.setSequenceNo(
                    sequenceNo++
            );

            /*
             * =========================
             * TRANSFER IN
             * =========================
             */

            InventoryMovement transferIn =
                    dataManager.create(
                            InventoryMovement.class
                    );

            transferIn.setTransaction(
                    transaction
            );

            transferIn.setItem(
                    item
            );

            transferIn.setWarehouse(
                    destinationWarehouse
            );

            transferIn.setProduct(
                    product
            );

            transferIn.setOccurredAt(
                    postedAt
            );

            transferIn.setSignedQuantity(
                    quantity
            );

            transferIn.setMovementType(
                    MovementType.TRANSFER_IN
            );

            transferIn.setSequenceNo(
                    sequenceNo++
            );

            /*
             * Lưu hai movement.
             */
            dataManager.save(
                    transferOut,
                    transferIn
            );
        }

        /*
         * =========================
         * POST TRANSACTION
         * =========================
         */

        transaction.setStatus(
                WarehouseTransactionStatus.POSTED
        );

        transaction.setPostedAt(
                postedAt
        );

        transaction.setPostedBy(
                currentAuthentication
                        .getUser()
                        .getUsername()
        );

        return dataManager.save(
                transaction
        );
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
            throw new WarehouseBusinessException(
                    "Không tìm thấy phiếu chuyển"
            );
        }

        if (transactions.size() > 1) {
            throw new WarehouseBusinessException(
                    "Dữ liệu không hợp lệ: nhiều phiếu có cùng ID"
            );
        }

        return transactions.get(0);
    }

    /**
     * Load transaction với fetch plan đầy đủ cho posting.
     */
    private WarehouseTransaction loadTransaction(
            UUID transactionId) {

        return dataManager
                .load(WarehouseTransaction.class)
                .id(transactionId)
                .fetchPlan(fpb -> fpb
                        .addFetchPlan(FetchPlan.BASE)

                        .add(
                                "sourceWarehouse",
                                FetchPlan.BASE
                        )

                        .add(
                                "destinationWarehouse",
                                FetchPlan.BASE
                        )

                        .add(
                                "items",
                                itemFp -> itemFp
                                        .addFetchPlan(
                                                FetchPlan.BASE
                                        )
                                        .add("lineNo")
                                        .add("quantity")
                                        .add(
                                                "product",
                                                FetchPlan.BASE
                                        )
                                        .add(
                                                "unit",
                                                FetchPlan.BASE
                                        )
                        )
                )
                .one();
    }

    /**
     * Validate header trước POST.
     */
    private void validateTransaction(
            WarehouseTransaction transaction) {

        if (transaction == null) {
            throw new WarehouseBusinessException(
                    "Không tìm thấy phiếu chuyển kho"
            );
        }

        if (transaction.getType()
                != WarehouseTransactionType.TRANSFER) {

            throw new WarehouseBusinessException(
                    "Chứng từ này không phải phiếu chuyển kho"
            );
        }

        WarehouseTransactionStatus status =
                transaction.getStatus();

        if (status != WarehouseTransactionStatus.DRAFT) {

            if (status
                    == WarehouseTransactionStatus.POSTED) {

                throw new WarehouseBusinessException(
                        "Phiếu chuyển kho đã được POSTED, không thể POST lần nữa"
                );
            }

            throw new WarehouseBusinessException(
                    "Chỉ phiếu DRAFT mới được phép POST"
            );
        }

        if (transaction.getSourceWarehouse() == null
                || transaction.getSourceWarehouse().getId() == null) {

            throw new WarehouseBusinessException(
                    "Phiếu chuyển kho chưa có kho nguồn"
            );
        }

        if (transaction.getDestinationWarehouse() == null
                || transaction.getDestinationWarehouse().getId() == null) {

            throw new WarehouseBusinessException(
                    "Phiếu chuyển kho chưa có kho đích"
            );
        }

        UUID sourceId =
                transaction.getSourceWarehouse().getId();

        UUID destinationId =
                transaction.getDestinationWarehouse().getId();

        /*
         * Không cho source = destination.
         */
        if (sourceId.equals(destinationId)) {

            throw new WarehouseBusinessException(
                    "Kho nguồn và kho đích không được giống nhau"
            );
        }

        Warehouse sourceWarehouse =
                dataManager.load(Warehouse.class)
                        .id(sourceId)
                        .optional()
                        .orElseThrow(() ->
                                new WarehouseBusinessException(
                                        "Không tìm thấy kho nguồn"
                                )
                        );

        if (!Boolean.TRUE.equals(
                sourceWarehouse.getActive())) {

            throw new WarehouseBusinessException(
                    "Kho nguồn đang ngừng hoạt động"
            );
        }

        Warehouse destinationWarehouse =
                dataManager.load(Warehouse.class)
                        .id(destinationId)
                        .optional()
                        .orElseThrow(() ->
                                new WarehouseBusinessException(
                                        "Không tìm thấy kho đích"
                                )
                        );

        if (!Boolean.TRUE.equals(
                destinationWarehouse.getActive())) {

            throw new WarehouseBusinessException(
                    "Kho đích đang ngừng hoạt động"
            );
        }

        if (transaction.getItems() == null
                || transaction.getItems().isEmpty()) {

            throw new WarehouseBusinessException(
                    "Phiếu chuyển kho phải có ít nhất 1 dòng sản phẩm"
            );
        }

        /*
         * Không cho trùng Product.
         */
        Set<UUID> productIds =
                new HashSet<>();

        for (TransactionItem item
                : transaction.getItems()) {

            if (item == null) {
                throw new WarehouseBusinessException(
                        "Phiếu chuyển kho chứa dòng dữ liệu không hợp lệ"
                );
            }

            if (item.getProduct() == null
                    || item.getProduct().getId() == null) {

                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": chưa có sản phẩm"
                );
            }

            if (!productIds.add(
                    item.getProduct().getId())) {

                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": sản phẩm bị trùng trong cùng một phiếu"
                );
            }

            Product product =
                    dataManager.load(Product.class)
                            .id(item.getProduct().getId())
                            .optional()
                            .orElseThrow(() ->
                                    new WarehouseBusinessException(
                                            "Dòng "
                                                    + item.getLineNo()
                                                    + ": không tìm thấy sản phẩm"
                                    )
                            );

            if (!Boolean.TRUE.equals(
                    product.getActive())) {

                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo()
                                + ": sản phẩm đang ngừng hoạt động"
                );
            }

            validateItem(
                    item,
                    product,
                    item.getQuantity()
            );
        }
    }

    /**
     * Validate từng item.
     */
    private void validateItem(
            TransactionItem item,
            Product product,
            BigDecimal quantity) {

        if (item == null) {
            throw new WarehouseBusinessException(
                    "Dòng sản phẩm không hợp lệ"
            );
        }

        if (product == null
                || product.getId() == null) {

            throw new WarehouseBusinessException(
                    "Dòng " + item.getLineNo()
                            + ": sản phẩm không hợp lệ"
            );
        }

        if (quantity == null) {

            throw new WarehouseBusinessException(
                    "Dòng " + item.getLineNo()
                            + ": số lượng không được để trống"
            );
        }

        if (quantity.signum() <= 0) {

            throw new WarehouseBusinessException(
                    "Dòng " + item.getLineNo()
                            + ": số lượng phải lớn hơn 0"
            );
        }

        if (quantity.scale()
                > MAX_QUANTITY_SCALE) {

            throw new WarehouseBusinessException(
                    "Dòng " + item.getLineNo()
                            + ": số lượng chỉ được tối đa "
                            + MAX_QUANTITY_SCALE
                            + " chữ số thập phân"
            );
        }

        int integerDigits =
                quantity.precision()
                        - quantity.scale();

        if (integerDigits
                > MAX_QUANTITY_INTEGER_DIGITS) {

            throw new WarehouseBusinessException(
                    "Dòng " + item.getLineNo()
                            + ": phần nguyên của số lượng vượt quá giới hạn cho phép"
            );
        }

        if (item.getUnit() == null
                || item.getUnit().getId() == null) {

            throw new WarehouseBusinessException(
                    "Dòng " + item.getLineNo()
                            + ": chưa có đơn vị tính"
            );
        }
    }

    /**
     * Tìm Inventory của destination.
     *
     * Nếu có thì lock pessimistic.
     * Nếu chưa có thì trả null để caller tạo mới.
     */
    private Inventory loadDestinationInventory(
            UUID warehouseId,
            UUID productId) {

        List<Inventory> inventories =
                dataManager
                        .load(Inventory.class)
                        .query("""
                                select e
                                from Inventory e
                                where e.warehouse.id = ?1
                                  and e.product.id = ?2
                                """,
                                warehouseId,
                                productId
                        )
                        .lockMode(
                                LockModeType.PESSIMISTIC_WRITE
                        )
                        .list();

        if (inventories.size() > 1) {

            throw new WarehouseBusinessException(
                    "Dữ liệu tồn kho không hợp lệ: "
                            + "một kho/sản phẩm có nhiều dòng Inventory"
            );
        }

        if (inventories.isEmpty()) {
            return null;
        }

        return inventories.get(0);
    }
}