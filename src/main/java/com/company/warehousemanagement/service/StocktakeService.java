package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.MovementType;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.StocktakeStatus;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehousePermissions;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.core.security.CurrentAuthentication;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StocktakeService {

    private final DataManager dataManager;
    private final UnconstrainedDataManager unconstrainedDataManager;
    private final AccessManager accessManager;
    private final CurrentAuthentication currentAuthentication;

    public StocktakeService(
            DataManager dataManager,
            UnconstrainedDataManager unconstrainedDataManager,
            AccessManager accessManager,
            CurrentAuthentication currentAuthentication) {
        this.dataManager = dataManager;
        this.unconstrainedDataManager = unconstrainedDataManager;
        this.accessManager = accessManager;
        this.currentAuthentication = currentAuthentication;
    }

    public void prepareDraft(Stocktake stocktake) {
        stocktake.setDocumentNo(
                "STK-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
        stocktake.setStatus(StocktakeStatus.DRAFT);
    }

    public List<SnapshotLine> loadSnapshot(UUID warehouseId) {
        checkPermission(
                WarehousePermissions.COUNT_STOCK,
                "Bạn không có quyền tạo snapshot kiểm kê"
        );
        loadAccessibleWarehouse(warehouseId);
        Map<UUID, SnapshotLine> linesByProduct = new HashMap<>();
        for (Inventory inventory : dataManager.load(Inventory.class)
                .query("select e from Inventory e where e.warehouse.id = ?1", warehouseId)
                .fetchPlan(fp -> fp.add("product", FetchPlan.BASE))
                .list()) {
            Product product = inventory.getProduct();
            linesByProduct.put(product.getId(), new SnapshotLine(
                    product, quantityOrZero(inventory.getQuantity())));
        }

        for (Product product : dataManager.load(Product.class)
                .query("select e from Product e where e.active = true order by e.code")
                .fetchPlan(FetchPlan.BASE)
                .list()) {
            linesByProduct.putIfAbsent(
                    product.getId(),
                    new SnapshotLine(product, BigDecimal.ZERO)
            );
        }
        List<SnapshotLine> lines = new ArrayList<>(linesByProduct.values());
        // Stable product order makes the generated snapshot deterministic and
        // helps subsequent stocktake approval acquire inventory locks consistently.
        lines.sort(Comparator
                .comparing((SnapshotLine line) -> line.product().getCode(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(line -> line.product().getId()));
        return lines;
    }

    /**
     * Employee submits a completed stocktake for Manager approval.
     * COUNTING -> PENDING_APPROVAL.
     * Inventory is not changed in this method.
     */
    @Transactional
    public Stocktake submit(UUID stocktakeId) {
        checkPermission(
                WarehousePermissions.COUNT_STOCK,
                "Bạn không có quyền gửi duyệt phiếu kiểm kê"
        );

        Stocktake stocktake = loadStocktakeForUpdate(stocktakeId);

        if (stocktake.getStatus() != StocktakeStatus.COUNTING) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu đang kiểm kê mới được gửi duyệt"
            );
        }

        loadAccessibleWarehouse(stocktake.getWarehouse().getId());

        if (stocktake.getSnapshotAt() == null) {
            throw new WarehouseBusinessException(
                    "Phiếu kiểm kê chưa có thời điểm snapshot"
            );
        }
        if (stocktake.getItems().isEmpty()) {
            throw new WarehouseBusinessException(
                    "Phiếu kiểm kê chưa có danh sách sản phẩm"
            );
        }

        for (StocktakeItem item : stocktake.getItems()) {
            validateCountedQuantity(item);
        }

        stocktake.setStatus(StocktakeStatus.PENDING_APPROVAL);
        return dataManager.save(stocktake);
    }

    @Transactional
    public Stocktake approve(UUID stocktakeId) {
        checkPermission(
                WarehousePermissions.APPROVE_STOCKTAKE,
                "Bạn không có quyền duyệt phiếu kiểm kê"
        );
        if (stocktakeId == null) {
            throw new WarehouseBusinessException("Chưa lưu phiếu kiểm kê");
        }

        Stocktake stocktake = dataManager.load(Stocktake.class)
                .query("select e from Stocktake e where e.id = ?1", stocktakeId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("warehouse", FetchPlan.BASE)
                        .add("items", item -> item.addFetchPlan(FetchPlan.BASE)
                                .add("product", product -> product.addFetchPlan(FetchPlan.BASE)
                                        .add("baseUnit", FetchPlan.BASE))))
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy phiếu kiểm kê"
                ));

        if (stocktake.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu đang chờ duyệt mới được duyệt"
            );
        }
        Warehouse warehouse = loadAccessibleWarehouse(stocktake.getWarehouse().getId());
        if (stocktake.getSnapshotAt() == null) {
            throw new WarehouseBusinessException(
                    "Phiếu kiểm kê chưa có thời điểm snapshot"
            );
        }
        if (stocktake.getItems().isEmpty()) {
            throw new WarehouseBusinessException(
                    "Phiếu kiểm kê chưa có danh sách sản phẩm"
            );
        }

        // Validate every line before changing any inventory or writing movements.
        List<StocktakeItem> itemsToProcess = new ArrayList<>(stocktake.getItems());
        for (StocktakeItem item : itemsToProcess) {
            validateCountedQuantity(item);
        }
        // A stable lock order reduces deadlock risk between concurrent stocktakes.
        itemsToProcess.sort(Comparator
                .comparing((StocktakeItem item) -> item.getProduct().getCode(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(item -> item.getProduct().getId()));

        OffsetDateTime approvedAt = OffsetDateTime.now();
        List<VarianceLine> inbound = new ArrayList<>();
        List<VarianceLine> outbound = new ArrayList<>();
        for (StocktakeItem item : itemsToProcess) {
            BigDecimal counted = item.getCountedQuantity();

            BigDecimal variance = counted.subtract(
                    quantityOrZero(item.getBookQuantity())
            );
            item.setVarianceQuantity(variance);

            Inventory inventory = loadOrCreateInventoryForUpdate(
                    warehouse,
                    item.getProduct()
            );
            BigDecimal adjustedQuantity =
                    quantityOrZero(inventory.getQuantity()).add(variance);
            if (adjustedQuantity.signum() < 0) {
                throw new WarehouseBusinessException(
                        "Điều chỉnh kiểm kê làm tồn kho âm cho sản phẩm "
                                + item.getProduct().getCode()
                );
            }
            inventory.setQuantity(adjustedQuantity);
            inventory.setUpdatedAt(approvedAt);
            unconstrainedDataManager.save(inventory);

            if (variance.signum() > 0) {
                inbound.add(new VarianceLine(item, variance));
            } else if (variance.signum() < 0) {
                outbound.add(new VarianceLine(item, variance.negate()));
            }
            unconstrainedDataManager.save(item);
        }

        if (!inbound.isEmpty()) {
            postVarianceTransaction(
                    stocktake, warehouse, inbound, true, approvedAt);
        }
        if (!outbound.isEmpty()) {
            postVarianceTransaction(
                    stocktake, warehouse, outbound, false, approvedAt);
        }

        stocktake.setStatus(StocktakeStatus.APPROVED);
        stocktake.setApprovedAt(approvedAt);
        return dataManager.save(stocktake);
    }

    /**
     * Manager rejects a pending stocktake.
     * PENDING_APPROVAL -> REJECTED; no Inventory/Movement changes occur.
     */
    @Transactional
    public Stocktake reject(UUID stocktakeId) {
        checkPermission(
                WarehousePermissions.REJECT_STOCKTAKE,
                "Bạn không có quyền từ chối phiếu kiểm kê"
        );

        Stocktake stocktake = loadStocktakeForUpdate(stocktakeId);

        if (stocktake.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu đang chờ duyệt mới được từ chối"
            );
        }

        loadAccessibleWarehouse(stocktake.getWarehouse().getId());
        stocktake.setStatus(StocktakeStatus.REJECTED);
        return dataManager.save(stocktake);
    }

    /**
     * Stocktake quantities are measured in the product's base unit. Zero is
     * valid for a physical count, unlike transaction line quantities which
     * must be strictly positive.
     */
    private void validateCountedQuantity(StocktakeItem item) {
        if (item == null || item.getProduct() == null) {
            throw new WarehouseBusinessException(
                    "Phiếu kiểm kê có dòng thiếu sản phẩm"
            );
        }

        BigDecimal counted = item.getCountedQuantity();
        if (counted == null || counted.signum() < 0) {
            throw new WarehouseBusinessException(
                    "Vui lòng nhập số đếm không âm cho tất cả sản phẩm"
            );
        }

        BigDecimal book = item.getBookQuantity();
        if (book == null || book.signum() < 0) {
            throw new WarehouseBusinessException(
                    "Số tồn snapshot không hợp lệ cho sản phẩm "
                            + item.getProduct().getCode()
            );
        }

        Product product = item.getProduct();
        if (product.getBaseUnit() == null) {
            throw new WarehouseBusinessException(
                    "Sản phẩm " + product.getCode() + " chưa có đơn vị cơ sở"
            );
        }
        Integer decimalScale = product.getBaseUnit().getDecimalScale();
        if (decimalScale == null || decimalScale < 0) {
            throw new WarehouseBusinessException(
                    "Đơn vị cơ sở của sản phẩm " + product.getCode()
                            + " có Decimal Scale không hợp lệ"
            );
        }

        int allowedScale = Math.min(3, decimalScale);
        validateStoredScale(counted, allowedScale, product.getCode(), "Số đếm");
        validateStoredScale(book, allowedScale, product.getCode(), "Số tồn snapshot");
    }

    private void validateStoredScale(
            BigDecimal value,
            int allowedScale,
            String productCode,
            String fieldName) {
        BigDecimal normalized = value.stripTrailingZeros();
        int actualScale = Math.max(normalized.scale(), 0);
        if (actualScale > allowedScale) {
            throw new WarehouseBusinessException(
                    fieldName + " của sản phẩm " + productCode
                            + " chỉ được tối đa " + allowedScale
                            + " chữ số thập phân"
            );
        }

        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > 16) {
            throw new WarehouseBusinessException(
                    fieldName + " của sản phẩm " + productCode
                            + " vượt giới hạn lưu trữ"
            );
        }
    }

    private Stocktake loadStocktakeForUpdate(UUID stocktakeId) {
        if (stocktakeId == null) {
            throw new WarehouseBusinessException("Chưa lưu phiếu kiểm kê");
        }

        return dataManager.load(Stocktake.class)
                .query("select e from Stocktake e where e.id = ?1", stocktakeId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("warehouse", FetchPlan.BASE)
                        .add("items", item -> item.addFetchPlan(FetchPlan.BASE)
                                .add("product", product -> product.addFetchPlan(FetchPlan.BASE)
                                        .add("baseUnit", FetchPlan.BASE))))
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy phiếu kiểm kê"
                ));
    }

    private void checkPermission(String permission, String errorMessage) {
        SpecificOperationAccessContext context =
                new SpecificOperationAccessContext(permission);
        accessManager.applyRegisteredConstraints(context);
        if (!context.isPermitted()) {
            throw new WarehouseBusinessException(errorMessage);
        }
    }

    private void postVarianceTransaction(
            Stocktake stocktake,
            Warehouse warehouse,
            List<VarianceLine> lines,
            boolean inbound,
            OffsetDateTime occurredAt) {
        WarehouseTransaction transaction =
                unconstrainedDataManager.create(WarehouseTransaction.class);
        transaction.setDocumentNo(stocktake.getDocumentNo()
                + (inbound ? "-IN" : "-OUT"));
        transaction.setType(WarehouseTransactionType.ADJUSTMENT);
        transaction.setStatus(WarehouseTransactionStatus.POSTED);
        transaction.setSourceWarehouse(warehouse);
        transaction.setAdjustmentDirection(inbound
                ? com.company.warehousemanagement.entity.AdjustmentDirection.IN
                : com.company.warehousemanagement.entity.AdjustmentDirection.OUT);
        transaction.setDocumentDate(LocalDate.now());
        transaction.setPostedAt(occurredAt);
        transaction.setPostedBy(currentAuthentication.getUser().getUsername());
        transaction.setReason("Stocktake " + stocktake.getDocumentNo());
        transaction = unconstrainedDataManager.save(transaction);
        int sequenceNo = 1;

        for (VarianceLine line : lines) {
            StocktakeItem stocktakeItem = line.item();
            Product product = stocktakeItem.getProduct();
            if (product.getBaseUnit() == null) {
                throw new WarehouseBusinessException(
                        "Sản phẩm " + product.getCode()
                                + " chưa có đơn vị cơ sở"
                );
            }

            TransactionItem transactionItem =
                    unconstrainedDataManager.create(TransactionItem.class);
            transactionItem.setTransaction(transaction);
            transactionItem.setLineNo(sequenceNo);
            transactionItem.setProduct(product);
            transactionItem.setUnit(product.getBaseUnit());
            transactionItem.setQuantity(line.quantity());
            transactionItem.setNote("Stocktake " + stocktake.getDocumentNo());
            transactionItem =
                    unconstrainedDataManager.save(transactionItem);
            transaction.addItem(transactionItem);

            InventoryMovement movement =
                    unconstrainedDataManager.create(InventoryMovement.class);
            movement.setTransaction(transaction);
            movement.setItem(transactionItem);
            movement.setWarehouse(warehouse);
            movement.setProduct(product);
            movement.setOccurredAt(occurredAt);
            movement.setSignedQuantity(inbound
                    ? line.quantity()
                    : line.quantity().negate());
            movement.setMovementType(inbound
                    ? MovementType.ADJUSTMENT_IN
                    : MovementType.ADJUSTMENT_OUT);
            movement.setSequenceNo(sequenceNo++);
            unconstrainedDataManager.save(movement);
        }
        unconstrainedDataManager.save(transaction);
    }

    private Inventory loadOrCreateInventoryForUpdate(
            Warehouse warehouse,
            Product product) {
        List<Inventory> inventories = unconstrainedDataManager
                .load(Inventory.class)
                .query("""
                        select e from Inventory e
                        where e.warehouse.id = ?1 and e.product.id = ?2
                        """, warehouse.getId(), product.getId())
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .list();
        if (inventories.size() > 1) {
            throw new WarehouseBusinessException(
                    "Có nhiều dòng tồn kho cho cùng kho và sản phẩm"
            );
        }
        if (inventories.isEmpty()) {
            Inventory inventory = unconstrainedDataManager.create(Inventory.class);
            inventory.setWarehouse(warehouse);
            inventory.setProduct(product);
            inventory.setQuantity(BigDecimal.ZERO);
            inventory.setReservedQuantity(BigDecimal.ZERO);
            inventory.setUpdatedAt(OffsetDateTime.now());
            return inventory;
        }
        return inventories.get(0);
    }

    private Warehouse loadAccessibleWarehouse(UUID warehouseId) {
        if (warehouseId == null) {
            throw new WarehouseBusinessException("Chưa chọn kho kiểm kê");
        }
        return dataManager.load(Warehouse.class)
                .id(warehouseId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Bạn không có quyền truy cập kho kiểm kê"
                ));
    }

    private BigDecimal quantityOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record SnapshotLine(Product product, BigDecimal bookQuantity) {
    }

    private record VarianceLine(StocktakeItem item, BigDecimal quantity) {
    }
}
