package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.PartnerType;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class ExportIssueService {

    private static final int MAX_QUANTITY_INTEGER_DIGITS = 16;
    private static final int MAX_QUANTITY_SCALE = 3;
    private static final int MAX_DOCUMENT_NO_LENGTH = 50;
    private static final int MAX_REASON_LENGTH = 1000;

    private final DataManager dataManager;
    private final WarehouseAuthorizationService authorizationService;

    public ExportIssueService(DataManager dataManager,
                              WarehouseAuthorizationService authorizationService) {
        this.dataManager = dataManager;
        this.authorizationService = authorizationService;
    }

    /**
     * Initializes a brand-new export document. No database write is performed here.
     */
    public void prepareNewDraft(WarehouseTransaction transaction) {
        requireEditDraft();
        if (transaction == null) {
            throw new WarehouseBusinessException("Không thể khởi tạo phiếu xuất rỗng");
        }

        transaction.setType(WarehouseTransactionType.EXPORT);
        transaction.setStatus(WarehouseTransactionStatus.DRAFT);
        transaction.setDocumentDate(LocalDate.now());
        transaction.setDocumentNo(generateDocumentNumber(transaction.getDocumentDate()));
    }

    /**
     * Business validation used before a DRAFT is saved from the UI.
     * The method intentionally does not change inventory.
     */
    public void validateDraft(WarehouseTransaction transaction) {
        requireEditDraft();
        if (transaction == null) {
            throw new WarehouseBusinessException("Phiếu xuất không được để trống");
        }

        if (transaction.getType() != WarehouseTransactionType.EXPORT) {
            throw new WarehouseBusinessException("Màn hình này chỉ dùng cho phiếu xuất kho");
        }

        WarehouseTransactionStatus status = transaction.getStatus();
        if (status != WarehouseTransactionStatus.DRAFT) {
            throw new WarehouseBusinessException("Chỉ phiếu DRAFT mới được phép chỉnh sửa hoặc lưu");
        }

        if (isBlank(transaction.getDocumentNo())) {
            throw new WarehouseBusinessException("Số phiếu không được để trống");
        }
        if (transaction.getDocumentNo().length() > MAX_DOCUMENT_NO_LENGTH) {
            throw new WarehouseBusinessException("Số phiếu không được dài quá " + MAX_DOCUMENT_NO_LENGTH + " ký tự");
        }

        if (transaction.getDocumentDate() == null) {
            throw new WarehouseBusinessException("Ngày chứng từ không được để trống");
        }

        Warehouse warehouse = transaction.getSourceWarehouse();
        if (warehouse == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn kho xuất");
        }
        Warehouse loadedWarehouse = loadWarehouse(warehouse.getId());
        if (!Boolean.TRUE.equals(loadedWarehouse.getActive())) {
            throw new WarehouseBusinessException("Kho xuất đang ngừng hoạt động");
        }

        Partner partner = transaction.getPartner();
        if (partner == null) {
            throw new WarehouseBusinessException("Bạn chưa chọn đối tác");
        }
        Partner loadedPartner = loadPartner(partner.getId());
        if (!Boolean.TRUE.equals(loadedPartner.getActive())) {
            throw new WarehouseBusinessException("Đối tác đang ngừng hoạt động");
        }
        PartnerType partnerType = loadedPartner.getPartnerType();
        if (partnerType != PartnerType.CUSTOMER && partnerType != PartnerType.BOTH) {
            throw new WarehouseBusinessException("Đối tác của phiếu xuất phải là khách hàng hoặc BOTH");
        }

        if (transaction.getReason() != null && transaction.getReason().length() > MAX_REASON_LENGTH) {
            throw new WarehouseBusinessException("Lý do không được dài quá " + MAX_REASON_LENGTH + " ký tự");
        }

        if (transaction.getItems() == null || transaction.getItems().isEmpty()) {
            throw new WarehouseBusinessException("Phiếu xuất phải có ít nhất 1 dòng sản phẩm");
        }

        Set<UUID> productIds = new HashSet<>();
        int expectedLineNo = 1;

        for (TransactionItem item : transaction.getItems()) {
            if (item == null) {
                throw new WarehouseBusinessException("Phiếu xuất chứa dòng dữ liệu không hợp lệ");
            }

            // Keep line numbers deterministic before persistence.
            item.setLineNo(expectedLineNo++);
            item.setTransaction(transaction);

            Product product = item.getProduct();
            if (product == null) {
                throw new WarehouseBusinessException("Dòng " + item.getLineNo() + ": chưa chọn sản phẩm");
            }

            UUID productId = product.getId();
            if (productId == null) {
                throw new WarehouseBusinessException("Dòng " + item.getLineNo() + ": sản phẩm chưa có ID hợp lệ");
            }
            if (!productIds.add(productId)) {
                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo() + ": sản phẩm bị trùng trong cùng một phiếu");
            }

            Product loadedProduct = loadProduct(productId);
            if (!Boolean.TRUE.equals(loadedProduct.getActive())) {
                throw new WarehouseBusinessException(
                        "Dòng " + item.getLineNo() + ": sản phẩm đang ngừng hoạt động");
            }

            BigDecimal quantity = item.getQuantity();
            validateQuantity(quantity, item.getLineNo());

            Unit unit = item.getUnit();
            if (unit == null) {
                throw new WarehouseBusinessException("Dòng " + item.getLineNo() + ": chưa chọn đơn vị tính");
            }
            if (unit.getId() == null) {
                throw new WarehouseBusinessException("Dòng " + item.getLineNo() + ": đơn vị tính không hợp lệ");
            }
            dataManager.load(Unit.class)
                    .id(unit.getId())
                    .optional()
                    .orElseThrow(() -> new WarehouseBusinessException(
                            "Dòng " + item.getLineNo() + ": không tìm thấy đơn vị tính"));
        }
    }

    private void validateQuantity(BigDecimal quantity, int lineNo) {
        if (quantity == null) {
            throw new WarehouseBusinessException("Dòng " + lineNo + ": số lượng không được để trống");
        }
        if (quantity.signum() <= 0) {
            throw new WarehouseBusinessException("Dòng " + lineNo + ": số lượng phải lớn hơn 0");
        }
        if (quantity.scale() > MAX_QUANTITY_SCALE) {
            throw new WarehouseBusinessException(
                    "Dòng " + lineNo + ": số lượng chỉ được tối đa " + MAX_QUANTITY_SCALE + " chữ số thập phân");
        }
        int integerDigits = quantity.precision() - quantity.scale();
        if (integerDigits > MAX_QUANTITY_INTEGER_DIGITS) {
            throw new WarehouseBusinessException(
                    "Dòng " + lineNo + ": phần nguyên của số lượng vượt quá giới hạn cho phép");
        }
    }

    private Warehouse loadWarehouse(UUID warehouseId) {
        if (warehouseId == null) {
            throw new WarehouseBusinessException("Kho xuất không hợp lệ");
        }
        return dataManager.load(Warehouse.class)
                .id(warehouseId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException("Không tìm thấy kho xuất"));
    }

    private Partner loadPartner(UUID partnerId) {
        if (partnerId == null) {
            throw new WarehouseBusinessException("Đối tác không hợp lệ");
        }
        return dataManager.load(Partner.class)
                .id(partnerId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException("Không tìm thấy đối tác"));
    }

    private Product loadProduct(UUID productId) {
        return dataManager.load(Product.class)
                .id(productId)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException("Không tìm thấy sản phẩm"));
    }

    private String generateDocumentNumber(LocalDate documentDate) {
        String datePart = documentDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomPart = UUID.randomUUID().toString()
                .substring(0, 8)
                .toUpperCase();
        return "EXP-" + datePart + "-" + randomPart;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void requireEditDraft() {
        authorizationService.require(
                WarehousePermissions.EDIT_DRAFT,
                "Bạn không có quyền tạo hoặc sửa phiếu xuất nháp"
        );
    }
}
