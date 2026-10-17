package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.AdjustmentDirection;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AdjustmentIssueService {

    private final DataManager dataManager;
    private final WarehouseAuthorizationService authorizationService;

    public AdjustmentIssueService(DataManager dataManager,
                                  WarehouseAuthorizationService authorizationService) {
        this.dataManager = dataManager;
        this.authorizationService = authorizationService;
    }

    public void prepareNewDraft(WarehouseTransaction transaction) {
        requireEditDraft();

        transaction.setType(WarehouseTransactionType.ADJUSTMENT);
        transaction.setStatus(WarehouseTransactionStatus.DRAFT);

        transaction.setDocumentDate(LocalDate.now());

        transaction.setDocumentNo(
                "ADJ-"
                        + LocalDate.now()
                        .toString()
                        .replace("-", "")
                        + "-"
                        + UUID.randomUUID()
        );

        transaction.setAdjustmentDirection(null);
        transaction.setReason(null);
    }

    public void validateDraft(WarehouseTransaction transaction) {
        requireEditDraft();

        if (transaction == null) {
            throw new WarehouseBusinessException(
                    "Không tìm thấy phiếu adjustment"
            );
        }

        if (transaction.getType()
                != WarehouseTransactionType.ADJUSTMENT) {

            throw new WarehouseBusinessException(
                    "Loại chứng từ phải là ADJUSTMENT"
            );
        }

        if (transaction.getStatus()
                != WarehouseTransactionStatus.DRAFT) {

            throw new WarehouseBusinessException(
                    "Chỉ phiếu DRAFT mới được chỉnh sửa"
            );
        }

        if (transaction.getDocumentNo() == null
                || transaction.getDocumentNo().isBlank()) {

            throw new WarehouseBusinessException(
                    "Document No không được để trống"
            );
        }

        if (transaction.getDocumentDate() == null) {

            throw new WarehouseBusinessException(
                    "Document Date không được để trống"
            );
        }

        /*
         * =========================
         * WAREHOUSE
         * =========================
         */

        if (transaction.getSourceWarehouse() == null) {

            throw new WarehouseBusinessException(
                    "Warehouse không được để trống"
            );
        }

        Warehouse warehouse =
                dataManager.load(Warehouse.class)
                        .id(transaction.getSourceWarehouse().getId())
                        .optional()
                        .orElseThrow(() ->
                                new WarehouseBusinessException(
                                        "Không tìm thấy Warehouse"
                                ));

        if (!warehouse.getActive()) {

            throw new WarehouseBusinessException(
                    "Warehouse đã bị inactive"
            );
        }

        /*
         * =========================
         * DIRECTION
         * =========================
         */

        if (transaction.getAdjustmentDirection() == null) {

            throw new WarehouseBusinessException(
                    "Adjustment Direction phải là IN hoặc OUT"
            );
        }

        /*
         * =========================
         * REASON
         * =========================
         */

        if (transaction.getReason() == null
                || transaction.getReason().isBlank()) {

            throw new WarehouseBusinessException(
                    "Adjustment bắt buộc phải nhập Reason"
            );
        }

        if (transaction.getReason().length() > 1000) {

            throw new WarehouseBusinessException(
                    "Reason không được vượt quá 1000 ký tự"
            );
        }

        /*
         * =========================
         * ITEMS
         * =========================
         */

        if (transaction.getItems() == null
                || transaction.getItems().isEmpty()) {

            throw new WarehouseBusinessException(
                    "Adjustment phải có ít nhất một sản phẩm"
            );
        }

        Set<UUID> productIds = new HashSet<>();

        int lineNo = 1;

        for (TransactionItem item : transaction.getItems()) {

            if (item == null) {
                continue;
            }

            item.setLineNo(lineNo++);
            item.setTransaction(transaction);

            /*
             * PRODUCT
             */

            if (item.getProduct() == null) {

                throw new WarehouseBusinessException(
                        "Product không được để trống"
                );
            }

            UUID productId = item.getProduct().getId();

            if (!productIds.add(productId)) {

                throw new WarehouseBusinessException(
                        "Không được có cùng một Product ở nhiều dòng"
                );
            }

            Product product =
                    dataManager.load(Product.class)
                            .id(productId)
                            .optional()
                            .orElseThrow(() ->
                                    new WarehouseBusinessException(
                                            "Không tìm thấy Product"
                                    ));

            if (!product.getActive()) {

                throw new WarehouseBusinessException(
                        "Product đã bị inactive"
                );
            }

            /*
             * QUANTITY
             */

            BigDecimal quantity = item.getQuantity();

            if (quantity == null
                    || quantity.signum() <= 0) {

                throw new WarehouseBusinessException(
                        "Quantity phải lớn hơn 0"
                );
            }

            if (quantity.scale() > 3) {

                throw new WarehouseBusinessException(
                        "Quantity chỉ được tối đa 3 chữ số thập phân"
                );
            }

            if (quantity.precision() - quantity.scale() > 16) {

                throw new WarehouseBusinessException(
                        "Phần nguyên của Quantity không được vượt quá 16 chữ số"
                );
            }

            /*
             * UNIT
             */

            if (item.getUnit() == null) {

                throw new WarehouseBusinessException(
                        "Unit không được để trống"
                );
            }

            dataManager.load(Unit.class)
                    .id(item.getUnit().getId())
                    .optional()
                    .orElseThrow(() ->
                            new WarehouseBusinessException(
                                    "Không tìm thấy Unit"
                            ));
        }
    }

    private void requireEditDraft() {
        authorizationService.require(
                WarehousePermissions.EDIT_DRAFT,
                "Bạn không có quyền tạo hoặc sửa phiếu điều chỉnh nháp"
        );
    }
}
