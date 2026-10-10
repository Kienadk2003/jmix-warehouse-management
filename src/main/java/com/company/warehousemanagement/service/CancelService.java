package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CancelService {

    private final DataManager dataManager;
    private final AccessManager accessManager;
    private final CurrentAuthentication currentAuthentication;

    public CancelService(
            DataManager dataManager,
            AccessManager accessManager,
            CurrentAuthentication currentAuthentication) {

        this.dataManager = dataManager;
        this.accessManager = accessManager;
        this.currentAuthentication = currentAuthentication;
    }

    @Transactional
    public WarehouseTransaction cancel(UUID transactionId) {

        if (transactionId == null) {
            throw new WarehouseBusinessException(
                    "Không thể cancel phiếu chưa được lưu"
            );
        }

        if (!currentAuthentication.isSet()) {
            throw new WarehouseBusinessException(
                    "Không xác định được người dùng hiện tại"
            );
        }

        /*
         * =========================================================
         * 1. CHECK SPECIFIC PERMISSION
         * =========================================================
         *
         * Chỉ role có:
         *
         * warehouse.transaction.cancel
         *
         * mới được cancel.
         */
        checkCancelPermission();

        /*
         * =========================================================
         * 2. LOAD TRANSACTION + PESSIMISTIC LOCK
         * =========================================================
         *
         * DataManager bình thường để row-level security
         * vẫn được áp dụng.
         */
        WarehouseTransaction transaction =
                loadTransactionForUpdate(transactionId);

        if (transaction == null) {
            throw new WarehouseBusinessException(
                    "Không tìm thấy phiếu cần cancel"
            );
        }

        /*
         * =========================================================
         * 3. CHỈ DRAFT MỚI ĐƯỢC CANCEL
         * =========================================================
         */
        if (transaction.getStatus()
                != WarehouseTransactionStatus.DRAFT) {

            throw new WarehouseBusinessException(
                    "Chỉ phiếu DRAFT mới được phép cancel"
            );
        }

        /*
         * =========================================================
         * 4. VALIDATE WAREHOUSE
         * =========================================================
         *
         * Cancel không thay đổi Inventory,
         * nhưng vẫn phải kiểm tra warehouse scope
         * vì user đang thao tác trên chứng từ thuộc warehouse.
         */
        validateWarehouseAccess(
                transaction.getSourceWarehouse() != null
                        ? transaction.getSourceWarehouse().getId()
                        : null,
                "kho nguồn"
        );

        /*
         * TRANSFER phải kiểm tra cả kho đích.
         */
        if (transaction.getType()
                == WarehouseTransactionType.TRANSFER) {

            validateWarehouseAccess(
                    transaction.getDestinationWarehouse() != null
                            ? transaction.getDestinationWarehouse().getId()
                            : null,
                    "kho đích"
            );
        }

        /*
         * =========================================================
         * 5. CANCEL
         * =========================================================
         */
        transaction.setStatus(
                WarehouseTransactionStatus.CANCELLED
        );

        dataManager.save(transaction);

        return transaction;
    }

    /*
     * =============================================================
     * SECURITY
     * =============================================================
     */

    private void checkCancelPermission() {

        SpecificOperationAccessContext context =
                new SpecificOperationAccessContext(
                        "warehouse.transaction.cancel"
                );

        accessManager.applyRegisteredConstraints(context);

        if (!context.isPermitted()) {
            throw new WarehouseBusinessException(
                    "Bạn không có quyền CANCEL phiếu kho"
            );
        }
    }

    /*
     * =============================================================
     * LOAD TRANSACTION
     * =============================================================
     */

    private WarehouseTransaction loadTransactionForUpdate(
            UUID transactionId) {

        List<WarehouseTransaction> transactions =
                dataManager
                        .load(WarehouseTransaction.class)
                        .query("""
                                select e
                                from WarehouseTransaction e
                                where e.id = ?1
                                """,
                                transactionId
                        )
                        .fetchPlan(fpb -> fpb

                                .addFetchPlan(
                                        FetchPlan.BASE
                                )

                                .add(
                                        "sourceWarehouse",
                                        FetchPlan.BASE
                                )

                                .add(
                                        "destinationWarehouse",
                                        FetchPlan.BASE
                                )

                                .add(
                                        "partner",
                                        FetchPlan.BASE
                                )

                                .add(
                                        "items",
                                        itemFp -> itemFp
                                                .addFetchPlan(
                                                        FetchPlan.BASE
                                                )
                                                .add(
                                                        "lineNo"
                                                )
                                                .add(
                                                        "quantity"
                                                )
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

    /*
     * =============================================================
     * WAREHOUSE ACCESS
     * =============================================================
     */

    private void validateWarehouseAccess(
            UUID warehouseId,
            String warehouseLabel) {

        if (warehouseId == null) {
            throw new WarehouseBusinessException(
                    "Không xác định được " + warehouseLabel
            );
        }

        boolean accessible =
                dataManager
                        .load(Warehouse.class)
                        .id(warehouseId)
                        .optional()
                        .isPresent();

        if (!accessible) {
            throw new WarehouseBusinessException(
                    "Bạn không có quyền thao tác trên "
                            + warehouseLabel
            );
        }
    }
}