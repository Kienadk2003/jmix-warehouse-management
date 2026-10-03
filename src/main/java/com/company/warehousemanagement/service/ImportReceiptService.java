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
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ImportReceiptService {

    private final DataManager dataManager;
    private final CurrentAuthentication currentAuthentication;

    public ImportReceiptService(DataManager dataManager,
                                CurrentAuthentication currentAuthentication) {
        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
    }

    @Transactional
    public WarehouseTransaction createDraft(CreateImportReceiptCommand command) {
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
        validateAddItemCommand(command);

        WarehouseTransaction receipt = loadImportReceipt(command.getReceiptId());
        ensureDraft(receipt);

        Product product = loadActiveProduct(command.getProductId());
        validateQuantity(command.getQuantity(), product);

        boolean duplicated = dataManager.load(TransactionItem.class)
                .query("select e from TransactionItem e "
                        + "where e.transaction.id = :receiptId "
                        + "and e.product.id = :productId")
                .parameter("receiptId", receipt.getId())
                .parameter("productId", product.getId())
                .optional()
                .isPresent();

        if (duplicated) {
            throw new WarehouseBusinessException("Sản phẩm đã có trong phiếu nhập");
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

    @Transactional
    public TransactionItem updateItem(UpdateImportReceiptItemCommand command) {
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
        if (itemId == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn dòng hàng cần xóa");
        }

        TransactionItem item = loadItem(itemId);
        WarehouseTransaction receipt = loadImportReceipt(item.getTransaction().getId());
        ensureDraft(receipt);
        dataManager.remove(item);
    }

    @Transactional
    public WarehouseTransaction confirm(UUID receiptId) {
        WarehouseTransaction receipt = loadImportReceipt(receiptId);
        ensureDraft(receipt);
        validateReceiptItems(loadItems(receipt.getId()));

        receipt.setStatus(WarehouseTransactionStatus.CONFIRMED);
        return dataManager.save(receipt);
    }

    @Transactional
    public WarehouseTransaction post(UUID receiptId) {
        WarehouseTransaction receipt = loadImportReceipt(receiptId);

        if (receipt.getStatus() != WarehouseTransactionStatus.CONFIRMED) {
            throw new WarehouseBusinessException(
                    "Chỉ được nhập kho với phiếu đã xác nhận"
            );
        }

        List<TransactionItem> items = loadItems(receipt.getId());
        validateReceiptItems(items);

        Warehouse destinationWarehouse = receipt.getDestinationWarehouse();
        if (destinationWarehouse == null) {
            throw new WarehouseBusinessException("Phiếu nhập chưa có kho nhận");
        }

        OffsetDateTime occurredAt = OffsetDateTime.now();
        int sequenceNo = 1;

        for (TransactionItem item : items) {
            Inventory inventory = loadOrCreateInventory(
                    destinationWarehouse,
                    item.getProduct()
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
            movement.setProduct(item.getProduct());
            movement.setOccurredAt(occurredAt);
            movement.setSignedQuantity(item.getQuantity());
            movement.setMovementType(MovementType.IMPORT);
            movement.setSequenceNo(sequenceNo++);
            dataManager.save(movement);
        }

        receipt.setStatus(WarehouseTransactionStatus.POSTED);
        receipt.setPostedAt(occurredAt);
        receipt.setPostedBy(currentUsername());
        return dataManager.save(receipt);
    }

    @Transactional
    public WarehouseTransaction cancel(UUID receiptId) {
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
        if (quantity == null || quantity.signum() <= 0) {
            throw new WarehouseBusinessException("Số lượng nhập phải lớn hơn 0");
        }
        if (product.getBaseUnit() == null) {
            throw new WarehouseBusinessException("Sản phẩm chưa có đơn vị cơ sở");
        }
        if (!Boolean.TRUE.equals(product.getBaseUnit().getActive())) {
            throw new WarehouseBusinessException("Đơn vị của sản phẩm đang ngừng hoạt động");
        }

        Integer allowedScale = product.getBaseUnit().getDecimalScale();
        int actualScale = Math.max(quantity.stripTrailingZeros().scale(), 0);
        if (allowedScale != null && actualScale > allowedScale) {
            throw new WarehouseBusinessException("Số lượng có quá nhiều chữ số thập phân");
        }
    }

    private void validateReceiptItems(List<TransactionItem> items) {
        if (items.isEmpty()) {
            throw new WarehouseBusinessException("Phiếu nhập phải có ít nhất một sản phẩm");
        }
        for (TransactionItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new WarehouseBusinessException(
                        "Phiếu có dòng hàng với số lượng không hợp lệ"
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

    private TransactionItem loadItem(UUID itemId) {
        return dataManager.load(TransactionItem.class)
                .id(itemId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy dòng hàng"
                ));
    }

    private List<TransactionItem> loadItems(UUID receiptId) {
        return dataManager.load(TransactionItem.class)
                .query("select e from TransactionItem e "
                        + "where e.transaction.id = :receiptId "
                        + "order by e.lineNo")
                .parameter("receiptId", receiptId)
                .list();
    }

    private void ensureDraft(WarehouseTransaction receipt) {
        if (receipt.getStatus() != WarehouseTransactionStatus.DRAFT) {
            throw new WarehouseBusinessException(
                    "Chỉ được sửa phiếu đang ở trạng thái nháp"
            );
        }
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

    private Inventory loadOrCreateInventory(Warehouse warehouse, Product product) {
        return dataManager.load(Inventory.class)
                .query("select e from Inventory e "
                        + "where e.warehouse.id = :warehouseId "
                        + "and e.product.id = :productId")
                .parameter("warehouseId", warehouse.getId())
                .parameter("productId", product.getId())
                .optional()
                .orElseGet(() -> {
                    Inventory inventory = dataManager.create(Inventory.class);
                    inventory.setWarehouse(warehouse);
                    inventory.setProduct(product);
                    inventory.setQuantity(BigDecimal.ZERO);
                    inventory.setReservedQuantity(BigDecimal.ZERO);
                    return inventory;
                });
    }

    private int nextLineNo(UUID receiptId) {
        return dataManager.load(TransactionItem.class)
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
}
