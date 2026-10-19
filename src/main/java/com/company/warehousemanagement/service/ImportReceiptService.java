package com.company.warehousemanagement.service;

import com.company.warehousemanagement.dto.AddImportReceiptItemCommand;
import com.company.warehousemanagement.dto.CreateImportReceiptCommand;
import com.company.warehousemanagement.dto.UpdateImportReceiptItemCommand;
import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.MovementType;
import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.PartnerType;
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
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.CurrentAuthentication;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportReceiptService {

    private final DataManager dataManager;
    private final UnconstrainedDataManager unconstrainedDataManager;
    private final CurrentAuthentication currentAuthentication;
    private final WarehouseAuthorizationService authorizationService;
    private final WarehouseTransactionWorkflowService workflowService;


    public ImportReceiptService(
            DataManager dataManager,
            UnconstrainedDataManager unconstrainedDataManager,
            CurrentAuthentication currentAuthentication,
            WarehouseTransactionWorkflowService workflowService,
            WarehouseAuthorizationService authorizationService) {

        this.dataManager = dataManager;
        this.unconstrainedDataManager = unconstrainedDataManager;
        this.currentAuthentication = currentAuthentication;
        this.workflowService = workflowService;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public WarehouseTransaction createDraft(CreateImportReceiptCommand command) {
        requireEditDraft();
        validateCreateCommand(command);

        Warehouse warehouse = loadActiveWarehouse(command.getWarehouseId());
        Partner supplier = loadActiveSupplier(command.getPartnerId());
        LocalDate documentDate = command.getDocumentDate() != null
                ? command.getDocumentDate()
                : LocalDate.now();

        WarehouseTransaction receipt = dataManager.create(WarehouseTransaction.class);
        receipt.setDocumentNo(generateDocumentNumber(documentDate));
        receipt.setType(WarehouseTransactionType.IMPORT);
        receipt.setStatus(WarehouseTransactionStatus.DRAFT);
        receipt.setDocumentDate(documentDate);
        receipt.setDestinationWarehouse(warehouse);
        receipt.setPartner(supplier);
        receipt.setReason(normalizeText(command.getReason()));

        return dataManager.save(receipt);
    }

    @Transactional
    public TransactionItem addItem(AddImportReceiptItemCommand command) {
        requireEditDraft();
        validateAddItemCommand(command);

        WarehouseTransaction receipt = loadImportReceipt(command.getReceiptId());
        ensureDraft(receipt);

        Product product = loadActiveProduct(command.getProductId());
        validateQuantity(command.getQuantity(), product);

        TransactionItem existingItem = unconstrainedDataManager.load(TransactionItem.class)
                .query("select e from TransactionItem e "
                        + "where e.transaction.id = :receiptId "
                        + "and e.product.id = :productId")
                .parameter("receiptId", receipt.getId())
                .parameter("productId", product.getId())
                .optional()
                .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity().add(command.getQuantity()));
            if ((existingItem.getNote() == null || existingItem.getNote().isBlank())
                    && command.getNote() != null && !command.getNote().isBlank()) {
                existingItem.setNote(normalizeText(command.getNote()));
            }
            return dataManager.save(existingItem);
        }

        TransactionItem item = dataManager.create(TransactionItem.class);
        item.setTransaction(receipt);
        item.setLineNo(nextLineNo(receipt.getId()));
        item.setProduct(product);
        item.setQuantity(command.getQuantity());
        item.setUnit(product.getBaseUnit());
        item.setNote(normalizeText(command.getNote()));

        return dataManager.save(item);
    }

    @Transactional(readOnly = true)
    public List<TransactionItem> getItems(UUID receiptId) {
        // Kiểm tra quyền READ và phạm vi kho trên phiếu cha trước khi
        // dùng loader nội bộ để lấy các dòng hàng của chính phiếu đó.
        loadImportReceipt(receiptId);
        return loadItems(receiptId);
    }

    @Transactional
    public TransactionItem updateItem(UpdateImportReceiptItemCommand command) {
        requireEditDraft();
        if (command == null || command.getItemId() == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn dòng hàng cần sửa");
        }

        TransactionItem item = loadItem(command.getItemId());
        WarehouseTransaction receipt = loadImportReceipt(item.getTransaction().getId());
        ensureDraft(receipt);

        Product product = loadActiveProduct(item.getProduct().getId());
        validateQuantity(command.getQuantity(), product);

        item.setQuantity(command.getQuantity());
        item.setNote(normalizeText(command.getNote()));
        return dataManager.save(item);
    }

    @Transactional
    public void removeItem(UUID itemId) {
        requireEditDraft();
        if (itemId == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn dòng hàng cần xóa");
        }

        TransactionItem item = loadItem(itemId);
        WarehouseTransaction receipt = loadImportReceipt(item.getTransaction().getId());
        ensureDraft(receipt);
        dataManager.remove(item);
    }


    @Transactional
    public WarehouseTransaction submit(UUID receiptId) {
        WarehouseTransaction receipt = loadImportReceipt(receiptId);

        ensureDraft(receipt);
        validateReceiptItems(loadItems(receipt.getId()));

        // Kiểm tra quyền SUBMIT và chuyển:
        // DRAFT -> PENDING_APPROVAL
        return workflowService.submit(receiptId);
    }

    /**
     * Tương thích tạm thời với màn hình cũ đang gọi confirm().
     * Sau này sẽ đổi nút trên giao diện thành Gửi duyệt.
     */
    @Transactional
    @Deprecated
    public WarehouseTransaction confirm(UUID receiptId) {
        return submit(receiptId);
    }

    /**
     * Manager approves a pending import receipt.
     * PENDING_APPROVAL -> APPROVED.
     * Inventory is not changed until post() is called.
     */
    @Transactional
    public WarehouseTransaction approve(UUID receiptId) {
        authorizationService.require(
                WarehousePermissions.APPROVE,
                "Bạn không có quyền duyệt phiếu nhập"
        );

        WarehouseTransaction receipt = loadImportReceiptForUpdate(receiptId);
        if (receipt.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu nhập đang chờ duyệt mới được duyệt"
            );
        }

        validateReceiptItems(loadItems(receipt.getId()));
        receipt.setStatus(WarehouseTransactionStatus.APPROVED);
        return dataManager.save(receipt);
    }

    /**
     * Manager rejects a pending import receipt.
     * PENDING_APPROVAL -> REJECTED.
     * The existing business reason is preserved.
     */
    @Transactional
    public WarehouseTransaction reject(UUID receiptId) {
        authorizationService.require(
                WarehousePermissions.REJECT,
                "Bạn không có quyền từ chối phiếu nhập"
        );

        WarehouseTransaction receipt = loadImportReceiptForUpdate(receiptId);
        if (receipt.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu nhập đang chờ duyệt mới được từ chối"
            );
        }

        receipt.setStatus(WarehouseTransactionStatus.REJECTED);
        return dataManager.save(receipt);
    }

    @Transactional
    public WarehouseTransaction post(UUID receiptId) {
        authorizationService.require(
                WarehousePermissions.POST,
                "Bạn không có quyền nhập kho"
        );
        WarehouseTransaction receipt = loadImportReceiptForUpdate(receiptId);

        if (receipt.getStatus() != WarehouseTransactionStatus.APPROVED) {
            if (receipt.getStatus() == WarehouseTransactionStatus.POSTED) {
                throw new WarehouseBusinessException(
                        "Phiếu nhập đã được nhập kho, không thể nhập kho lần nữa"
                );
            }

            throw new WarehouseBusinessException(
                    "Chỉ được nhập kho với phiếu đã được quản lý duyệt"
            );
        }

        List<TransactionItem> items = loadItems(receipt.getId());
        validateReceiptItems(items);

        if (receipt.getDestinationWarehouse() == null
                || receipt.getDestinationWarehouse().getId() == null) {
            throw new WarehouseBusinessException("Phiếu nhập chưa có kho nhận");
        }

        // Khóa kho nhận để tuần tự hóa các lần POST IMPORT cùng kho,
        // đặc biệt khi Inventory của một sản phẩm chưa tồn tại.
        // Mọi posting handler khác cần dùng cùng quy ước khóa nếu muốn
        // bảo đảm đồng bộ toàn hệ thống khi tạo Inventory lần đầu.
        Warehouse destinationWarehouse = loadActiveWarehouseForPosting(
                receipt.getDestinationWarehouse().getId()
        );

        OffsetDateTime occurredAt = OffsetDateTime.now();
        int sequenceNo = 1;

        for (TransactionItem item : items) {
            Product product = loadActiveProduct(item.getProduct().getId());
            validateQuantity(item.getQuantity(), product);

            Inventory inventory = loadOrCreateInventory(
                    destinationWarehouse,
                    product
            );

            BigDecimal currentQuantity = inventory.getQuantity() == null
                    ? BigDecimal.ZERO
                    : inventory.getQuantity();
            inventory.setQuantity(currentQuantity.add(item.getQuantity()));
            inventory.setUpdatedAt(occurredAt);
            dataManager.save(inventory);

            InventoryMovement movement = dataManager.create(InventoryMovement.class);
            movement.setTransaction(receipt);
            movement.setItem(item);
            movement.setWarehouse(destinationWarehouse);
            movement.setProduct(product);
            movement.setOccurredAt(occurredAt);
            movement.setSignedQuantity(item.getQuantity());
            movement.setMovementType(MovementType.IMPORT);
            movement.setSequenceNo(sequenceNo++);
            dataManager.save(movement);
        }

        receipt.setStatus(WarehouseTransactionStatus.POSTED);
        receipt.setPostedAt(occurredAt);
        receipt.setPostedBy(currentUsername());

        WarehouseTransaction postedReceipt =
                dataManager.save(receipt);

        return postedReceipt;
    }

    @Transactional
    public WarehouseTransaction cancel(UUID receiptId) {
        authorizationService.require(
                WarehousePermissions.CANCEL,
                "Bạn không có quyền hủy phiếu nhập"
        );
        WarehouseTransaction receipt = loadImportReceipt(receiptId);

        if (receipt.getStatus() != WarehouseTransactionStatus.DRAFT
                && receipt.getStatus() != WarehouseTransactionStatus.CONFIRMED) {
            throw new WarehouseBusinessException(
                    "Chỉ được hủy phiếu nháp hoặc phiếu đã xác nhận"
            );
        }

        receipt.setStatus(WarehouseTransactionStatus.CANCELLED);
        return dataManager.save(receipt);
    }

    private void validateCreateCommand(CreateImportReceiptCommand command) {
        if (command == null) {
            throw new WarehouseBusinessException("Dữ liệu tạo phiếu không được để trống");
        }
        if (command.getWarehouseId() == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn kho nhận");
        }
        if (command.getPartnerId() == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn nhà cung cấp");
        }
    }

    private void validateAddItemCommand(AddImportReceiptItemCommand command) {
        if (command == null) {
            throw new WarehouseBusinessException("Dữ liệu dòng hàng không được để trống");
        }
        if (command.getReceiptId() == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn phiếu nhập");
        }
        if (command.getProductId() == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn sản phẩm");
        }
    }

    private void validateQuantity(BigDecimal quantity, Product product) {
        ProductUnitQuantityValidator.validate(
                dataManager, product,
                product == null ? null : product.getBaseUnit(),
                quantity, null);
    }

    private void validateReceiptItems(List<TransactionItem> items) {
        if (items == null || items.isEmpty()) {
            throw new WarehouseBusinessException("Phiếu nhập phải có ít nhất một sản phẩm");
        }

        Set<UUID> productIds = new HashSet<>();
        for (TransactionItem item : items) {
            if (item == null || item.getProduct() == null
                    || item.getProduct().getId() == null) {
                throw new WarehouseBusinessException(
                        "Phiếu có dòng hàng chưa chọn sản phẩm"
                );
            }
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new WarehouseBusinessException(
                        "Phiếu có dòng hàng với số lượng không hợp lệ"
                );
            }
            Product validatedProduct = loadActiveProduct(item.getProduct().getId());
            ProductUnitQuantityValidator.validate(
                    dataManager, validatedProduct, item.getUnit(),
                    item.getQuantity(), item.getLineNo());
            if (!productIds.add(item.getProduct().getId())) {
                throw new WarehouseBusinessException(
                        "Phiếu nhập có sản phẩm bị trùng; hãy gộp thành một dòng hàng"
                );
            }
        }
    }

    private WarehouseTransaction loadImportReceipt(UUID receiptId) {
        if (receiptId == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn phiếu nhập");
        }

        WarehouseTransaction receipt = dataManager.load(WarehouseTransaction.class)
                .id(receiptId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy phiếu nhập"
                ));

        if (receipt.getType() != WarehouseTransactionType.IMPORT) {
            throw new WarehouseBusinessException("Chứng từ không phải phiếu nhập kho");
        }
        return receipt;
    }

    private WarehouseTransaction loadImportReceiptForUpdate(UUID receiptId) {
        if (receiptId == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn phiếu nhập");
        }

        List<WarehouseTransaction> receipts = dataManager
                .load(WarehouseTransaction.class)
                .query("select e from WarehouseTransaction e where e.id = :receiptId")
                .parameter("receiptId", receiptId)
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .list();

        if (receipts.isEmpty()) {
            throw new WarehouseBusinessException("Không tìm thấy phiếu nhập");
        }

        WarehouseTransaction receipt = receipts.get(0);
        if (receipt.getType() != WarehouseTransactionType.IMPORT) {
            throw new WarehouseBusinessException("Chứng từ không phải phiếu nhập kho");
        }
        return receipt;
    }

    private TransactionItem loadItem(UUID itemId) {
        return unconstrainedDataManager.load(TransactionItem.class)
                .id(itemId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy dòng hàng"
                ));
    }

    private List<TransactionItem> loadItems(UUID receiptId) {
        return unconstrainedDataManager.load(TransactionItem.class)
                .query("select e from TransactionItem e "
                        + "where e.transaction.id = :receiptId "
                        + "order by e.lineNo")
                .parameter("receiptId", receiptId)
                .fetchPlan(fetchPlan -> fetchPlan
                        .addFetchPlan(FetchPlan.BASE)
                        .add("transaction", FetchPlan.BASE)
                        .add("product", FetchPlan.BASE)
                        .add("unit", FetchPlan.BASE))
                .list();
    }

    private void ensureDraft(WarehouseTransaction receipt) {
        if (receipt.getStatus() != WarehouseTransactionStatus.DRAFT) {
            throw new WarehouseBusinessException(
                    "Chỉ được sửa phiếu đang ở trạng thái nháp"
            );
        }
    }

    private Warehouse loadActiveWarehouseForPosting(UUID warehouseId) {
        List<Warehouse> warehouses = dataManager.load(Warehouse.class)
                .query("select e from Warehouse e where e.id = :warehouseId")
                .parameter("warehouseId", warehouseId)
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .list();

        if (warehouses.isEmpty()) {
            throw new WarehouseBusinessException("Không tìm thấy kho nhận");
        }

        Warehouse warehouse = warehouses.get(0);
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new WarehouseBusinessException("Kho nhận đang ngừng hoạt động");
        }
        return warehouse;
    }

    private Warehouse loadActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = dataManager.load(Warehouse.class)
                .id(warehouseId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy kho nhận"
                ));
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new WarehouseBusinessException("Kho nhận đang ngừng hoạt động");
        }
        return warehouse;
    }

    private Partner loadActiveSupplier(UUID partnerId) {
        Partner partner = dataManager.load(Partner.class)
                .id(partnerId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy đối tác"
                ));
        if (!Boolean.TRUE.equals(partner.getActive())) {
            throw new WarehouseBusinessException("Đối tác đang ngừng hoạt động");
        }

        PartnerType type = partner.getPartnerType();
        if (type != PartnerType.SUPPLIER && type != PartnerType.BOTH) {
            throw new WarehouseBusinessException("Đối tác không phải nhà cung cấp");
        }
        return partner;
    }

    private Product loadActiveProduct(UUID productId) {
        Product product = dataManager.load(Product.class)
                .id(productId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy sản phẩm"
                ));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new WarehouseBusinessException("Sản phẩm đang ngừng hoạt động");
        }
        return product;
    }


    private Inventory loadOrCreateInventory(
            Warehouse warehouse,
            Product product) {

        List<Inventory> inventories = dataManager
                .load(Inventory.class)
                .query("select e from Inventory e "
                        + "where e.warehouse.id = :warehouseId "
                        + "and e.product.id = :productId")
                .parameter("warehouseId", warehouse.getId())
                .parameter("productId", product.getId())
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .list();

        if (inventories.size() > 1) {
            throw new WarehouseBusinessException(
                    "Dữ liệu tồn kho không hợp lệ: "
                            + "một kho/sản phẩm có nhiều dòng Inventory"
            );
        }

        if (!inventories.isEmpty()) {
            return inventories.get(0);
        }

        Inventory inventory = dataManager.create(Inventory.class);
        inventory.setWarehouse(warehouse);
        inventory.setProduct(product);
        inventory.setQuantity(BigDecimal.ZERO);
        inventory.setReservedQuantity(BigDecimal.ZERO);

        return inventory;
    }


    private int nextLineNo(UUID receiptId) {
        return unconstrainedDataManager.load(TransactionItem.class)
                .query("select e from TransactionItem e "
                        + "where e.transaction.id = :receiptId "
                        + "order by e.lineNo desc")
                .parameter("receiptId", receiptId)
                .maxResults(1)
                .optional()
                .map(TransactionItem::getLineNo)
                .orElse(0) + 1;
    }

    private String currentUsername() {
        if (!currentAuthentication.isSet()) {
            return "system";
        }
        return currentAuthentication.getUser().getUsername();
    }

    private String generateDocumentNumber(LocalDate documentDate) {
        String datePart = documentDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomPart = UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
        return "IMP-" + datePart + "-" + randomPart;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void requireEditDraft() {
        authorizationService.require(
                WarehousePermissions.EDIT_DRAFT,
                "Bạn không có quyền tạo hoặc sửa phiếu nháp"
        );
    }
}
