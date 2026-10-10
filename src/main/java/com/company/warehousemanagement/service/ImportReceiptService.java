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
import com.company.warehousemanagement.event.WarehouseTransactionPostedEvent;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final WarehouseAuthorizationService authorizationService;
    private final WarehouseTransactionWorkflowService workflowService;


    public ImportReceiptService(
            DataManager dataManager,
            UnconstrainedDataManager unconstrainedDataManager,
            CurrentAuthentication currentAuthentication,
            ApplicationEventPublisher eventPublisher,
            WarehouseTransactionWorkflowService workflowService,
            WarehouseAuthorizationService authorizationService) {

        this.dataManager = dataManager;
        this.unconstrainedDataManager = unconstrainedDataManager;
        this.currentAuthentication = currentAuthentication;
        this.eventPublisher = eventPublisher;
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

        boolean duplicated = unconstrainedDataManager.load(TransactionItem.class)
                .query("select e from TransactionItem e "
                        + "where e.transaction.id = :receiptId "
                        + "and e.product.id = :productId")
                .parameter("receiptId", receipt.getId())
                .parameter("productId", product.getId())
                .optional()
                .isPresent();

        if (duplicated) {
            throw new WarehouseBusinessException("Sáº£n pháº©m Ä‘Ã£ cÃ³ trong phiáº¿u nháº­p");
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
        // Kiá»ƒm tra quyá»�n READ vÃ  Warehouse Scope trÃªn phiáº¿u cha trÆ°á»›c khi
        // dÃ¹ng loader ná»™i bá»™ Ä‘á»ƒ láº¥y cÃ¡c dÃ²ng hÃ ng cá»§a chÃ­nh phiáº¿u Ä‘Ã³.
        loadImportReceipt(receiptId);
        return loadItems(receiptId);
    }

    @Transactional
    public TransactionItem updateItem(UpdateImportReceiptItemCommand command) {
        requireEditDraft();
        if (command == null || command.getItemId() == null) {
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n dÃ²ng hÃ ng cáº§n sá»­a");
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
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n dÃ²ng hÃ ng cáº§n xÃ³a");
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

        // Kiá»ƒm tra quyá»�n SUBMIT vÃ  chuyá»ƒn:
        // DRAFT -> PENDING_APPROVAL
        return workflowService.submit(receiptId);
    }

    /**
     * TÆ°Æ¡ng thÃ­ch táº¡m thá»�i vá»›i mÃ n hÃ¬nh cÅ© Ä‘ang gá»�i confirm().
     * Sau nÃ y sáº½ Ä‘á»•i nÃºt trÃªn giao diá»‡n thÃ nh Gá»­i duyá»‡t.
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
                "Báº¡n khÃ´ng cÃ³ quyá»�n duyá»‡t phiáº¿u nháº­p"
        );

        WarehouseTransaction receipt = loadImportReceiptForUpdate(receiptId);
        if (receipt.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chá»‰ phiáº¿u nháº­p Ä‘ang chá»� duyá»‡t má»›i Ä‘Æ°á»£c duyá»‡t"
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
                "Báº¡n khÃ´ng cÃ³ quyá»�n tá»« chá»‘i phiáº¿u nháº­p"
        );

        WarehouseTransaction receipt = loadImportReceiptForUpdate(receiptId);
        if (receipt.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chá»‰ phiáº¿u nháº­p Ä‘ang chá»� duyá»‡t má»›i Ä‘Æ°á»£c tá»« chá»‘i"
            );
        }

        receipt.setStatus(WarehouseTransactionStatus.REJECTED);
        return dataManager.save(receipt);
    }

    @Transactional
    public WarehouseTransaction post(UUID receiptId) {
        authorizationService.require(
                WarehousePermissions.POST,
                "Báº¡n khÃ´ng cÃ³ quyá»�n nháº­p kho"
        );
        WarehouseTransaction receipt = loadImportReceiptForUpdate(receiptId);

        if (receipt.getStatus() != WarehouseTransactionStatus.APPROVED) {
            if (receipt.getStatus() == WarehouseTransactionStatus.POSTED) {
                throw new WarehouseBusinessException(
                        "Phiáº¿u nháº­p Ä‘Ã£ Ä‘Æ°á»£c nháº­p kho, khÃ´ng thá»ƒ nháº­p kho láº§n ná»¯a"
                );
            }

            throw new WarehouseBusinessException(
                    "Chá»‰ Ä‘Æ°á»£c nháº­p kho vá»›i phiáº¿u Ä‘Ã£ Ä‘Æ°á»£c Manager duyá»‡t"
            );
        }

        List<TransactionItem> items = loadItems(receipt.getId());
        validateReceiptItems(items);

        if (receipt.getDestinationWarehouse() == null
                || receipt.getDestinationWarehouse().getId() == null) {
            throw new WarehouseBusinessException("Phiáº¿u nháº­p chÆ°a cÃ³ kho nháº­n");
        }

        // KhÃ³a kho nháº­n Ä‘á»ƒ tuáº§n tá»± hÃ³a cÃ¡c láº§n POST IMPORT cÃ¹ng kho,
        // Ä‘áº·c biá»‡t khi Inventory cá»§a má»™t sáº£n pháº©m chÆ°a tá»“n táº¡i.
        // Má»�i posting handler khÃ¡c cáº§n dÃ¹ng cÃ¹ng quy Æ°á»›c khÃ³a náº¿u muá»‘n
        // báº£o Ä‘áº£m Ä‘á»“ng bá»™ toÃ n há»‡ thá»‘ng khi táº¡o Inventory láº§n Ä‘áº§u.
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

        eventPublisher.publishEvent(
                new WarehouseTransactionPostedEvent(
                        postedReceipt.getId()
                )
        );

        return postedReceipt;
    }

    @Transactional
    public WarehouseTransaction cancel(UUID receiptId) {
        authorizationService.require(
                WarehousePermissions.CANCEL,
                "Báº¡n khÃ´ng cÃ³ quyá»�n há»§y phiáº¿u nháº­p"
        );
        WarehouseTransaction receipt = loadImportReceipt(receiptId);

        if (receipt.getStatus() != WarehouseTransactionStatus.DRAFT
                && receipt.getStatus() != WarehouseTransactionStatus.CONFIRMED) {
            throw new WarehouseBusinessException(
                    "Chá»‰ Ä‘Æ°á»£c há»§y phiáº¿u nhÃ¡p hoáº·c phiáº¿u Ä‘Ã£ xÃ¡c nháº­n"
            );
        }

        receipt.setStatus(WarehouseTransactionStatus.CANCELLED);
        return dataManager.save(receipt);
    }

    private void validateCreateCommand(CreateImportReceiptCommand command) {
        if (command == null) {
            throw new WarehouseBusinessException("Dá»¯ liá»‡u táº¡o phiáº¿u khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng");
        }
        if (command.getWarehouseId() == null) {
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n kho nháº­n");
        }
        if (command.getPartnerId() == null) {
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n nhÃ  cung cáº¥p");
        }
    }

    private void validateAddItemCommand(AddImportReceiptItemCommand command) {
        if (command == null) {
            throw new WarehouseBusinessException("Dá»¯ liá»‡u dÃ²ng hÃ ng khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng");
        }
        if (command.getReceiptId() == null) {
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n phiáº¿u nháº­p");
        }
        if (command.getProductId() == null) {
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n sáº£n pháº©m");
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
            throw new WarehouseBusinessException("Phiáº¿u nháº­p pháº£i cÃ³ Ã­t nháº¥t má»™t sáº£n pháº©m");
        }

        Set<UUID> productIds = new HashSet<>();
        for (TransactionItem item : items) {
            if (item == null || item.getProduct() == null
                    || item.getProduct().getId() == null) {
                throw new WarehouseBusinessException(
                        "Phiáº¿u cÃ³ dÃ²ng hÃ ng chÆ°a chá»�n sáº£n pháº©m"
                );
            }
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw new WarehouseBusinessException(
                        "Phiáº¿u cÃ³ dÃ²ng hÃ ng vá»›i sá»‘ lÆ°á»£ng khÃ´ng há»£p lá»‡"
                );
            }
            Product validatedProduct = loadActiveProduct(item.getProduct().getId());
            ProductUnitQuantityValidator.validate(
                    dataManager, validatedProduct, item.getUnit(),
                    item.getQuantity(), item.getLineNo());
            if (!productIds.add(item.getProduct().getId())) {
                throw new WarehouseBusinessException(
                        "Phiáº¿u nháº­p cÃ³ sáº£n pháº©m bá»‹ trÃ¹ng; hÃ£y gá»™p thÃ nh má»™t dÃ²ng hÃ ng"
                );
            }
        }
    }

    private WarehouseTransaction loadImportReceipt(UUID receiptId) {
        if (receiptId == null) {
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n phiáº¿u nháº­p");
        }

        WarehouseTransaction receipt = dataManager.load(WarehouseTransaction.class)
                .id(receiptId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "KhÃ´ng tÃ¬m tháº¥y phiáº¿u nháº­p"
                ));

        if (receipt.getType() != WarehouseTransactionType.IMPORT) {
            throw new WarehouseBusinessException("Chá»©ng tá»« khÃ´ng pháº£i phiáº¿u nháº­p kho");
        }
        return receipt;
    }

    private WarehouseTransaction loadImportReceiptForUpdate(UUID receiptId) {
        if (receiptId == null) {
            throw new WarehouseBusinessException("Báº¡n chÆ°a chá»�n phiáº¿u nháº­p");
        }

        List<WarehouseTransaction> receipts = dataManager
                .load(WarehouseTransaction.class)
                .query("select e from WarehouseTransaction e where e.id = :receiptId")
                .parameter("receiptId", receiptId)
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .list();

        if (receipts.isEmpty()) {
            throw new WarehouseBusinessException("KhÃ´ng tÃ¬m tháº¥y phiáº¿u nháº­p");
        }

        WarehouseTransaction receipt = receipts.get(0);
        if (receipt.getType() != WarehouseTransactionType.IMPORT) {
            throw new WarehouseBusinessException("Chá»©ng tá»« khÃ´ng pháº£i phiáº¿u nháº­p kho");
        }
        return receipt;
    }

    private TransactionItem loadItem(UUID itemId) {
        return unconstrainedDataManager.load(TransactionItem.class)
                .id(itemId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "KhÃ´ng tÃ¬m tháº¥y dÃ²ng hÃ ng"
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
                    "Chá»‰ Ä‘Æ°á»£c sá»­a phiáº¿u Ä‘ang á»Ÿ tráº¡ng thÃ¡i nhÃ¡p"
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
            throw new WarehouseBusinessException("KhÃ´ng tÃ¬m tháº¥y kho nháº­n");
        }

        Warehouse warehouse = warehouses.get(0);
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new WarehouseBusinessException("Kho nháº­n Ä‘ang ngá»«ng hoáº¡t Ä‘á»™ng");
        }
        return warehouse;
    }

    private Warehouse loadActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = dataManager.load(Warehouse.class)
                .id(warehouseId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "KhÃ´ng tÃ¬m tháº¥y kho nháº­n"
                ));
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new WarehouseBusinessException("Kho nháº­n Ä‘ang ngá»«ng hoáº¡t Ä‘á»™ng");
        }
        return warehouse;
    }

    private Partner loadActiveSupplier(UUID partnerId) {
        Partner partner = dataManager.load(Partner.class)
                .id(partnerId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "KhÃ´ng tÃ¬m tháº¥y Ä‘á»‘i tÃ¡c"
                ));
        if (!Boolean.TRUE.equals(partner.getActive())) {
            throw new WarehouseBusinessException("Ä�á»‘i tÃ¡c Ä‘ang ngá»«ng hoáº¡t Ä‘á»™ng");
        }

        PartnerType type = partner.getPartnerType();
        if (type != PartnerType.SUPPLIER && type != PartnerType.BOTH) {
            throw new WarehouseBusinessException("Ä�á»‘i tÃ¡c khÃ´ng pháº£i nhÃ  cung cáº¥p");
        }
        return partner;
    }

    private Product loadActiveProduct(UUID productId) {
        Product product = dataManager.load(Product.class)
                .id(productId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "KhÃ´ng tÃ¬m tháº¥y sáº£n pháº©m"
                ));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new WarehouseBusinessException("Sáº£n pháº©m Ä‘ang ngá»«ng hoáº¡t Ä‘á»™ng");
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
                    "Dá»¯ liá»‡u tá»“n kho khÃ´ng há»£p lá»‡: "
                            + "má»™t kho/sáº£n pháº©m cÃ³ nhiá»�u dÃ²ng Inventory"
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
                "Báº¡n khÃ´ng cÃ³ quyá»�n táº¡o hoáº·c sá»­a phiáº¿u nhÃ¡p"
        );
    }
}
