package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehousePermissions;
import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.util.UUID;

@Service
public class WarehouseTransactionWorkflowService {

    private final DataManager dataManager;
    private final AccessManager accessManager;

    public WarehouseTransactionWorkflowService(
            DataManager dataManager,
            AccessManager accessManager
    ) {
        this.dataManager = dataManager;
        this.accessManager = accessManager;
    }

    @Transactional
    public WarehouseTransaction submit(UUID transactionId) {
        WarehouseTransaction transaction = loadTransaction(transactionId);
        checkPermission(WarehousePermissions.SUBMIT);

        if (transaction.getStatus() != WarehouseTransactionStatus.DRAFT) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu ở trạng thái DRAFT mới được gửi duyệt."
            );
        }

        transaction.setStatus(WarehouseTransactionStatus.PENDING_APPROVAL);
        return dataManager.save(transaction);
    }

    @Transactional
    public WarehouseTransaction approve(UUID transactionId) {
        WarehouseTransaction transaction = loadTransaction(transactionId);
        checkPermission(WarehousePermissions.APPROVE);

        if (transaction.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu ở trạng thái PENDING_APPROVAL mới được duyệt."
            );
        }

        transaction.setStatus(WarehouseTransactionStatus.APPROVED);
        return dataManager.save(transaction);
    }

    @Transactional
    public WarehouseTransaction reject(UUID transactionId, String reason) {
        WarehouseTransaction transaction = loadTransaction(transactionId);
        checkPermission(WarehousePermissions.REJECT);

        if (transaction.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            throw new WarehouseBusinessException(
                    "Chỉ phiếu ở trạng thái PENDING_APPROVAL mới được từ chối."
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new WarehouseBusinessException("Vui lòng nhập lý do từ chối.");
        }

        // Uses the existing reason field until a dedicated rejectionReason field is added.
        transaction.setReason(reason.trim());
        transaction.setStatus(WarehouseTransactionStatus.REJECTED);
        return dataManager.save(transaction);
    }

    private WarehouseTransaction loadTransaction(UUID transactionId) {
        if (transactionId == null) {
            throw new WarehouseBusinessException("Transaction ID không được để trống.");
        }
        // Lock the transaction row until this workflow transition commits.
        // This prevents concurrent Submit/Approve/Reject requests from both
        // acting on the same previously-read status.
        return dataManager.load(WarehouseTransaction.class)
                .query("select e from WarehouseTransaction e where e.id = :transactionId")
                .parameter("transactionId", transactionId)
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        "Không tìm thấy phiếu nghiệp vụ: " + transactionId
                ));
    }

    private void checkPermission(String permission) {
        SpecificOperationAccessContext context =
                new SpecificOperationAccessContext(permission);
        accessManager.applyRegisteredConstraints(context);

        if (!context.isPermitted()) {
            throw new WarehouseBusinessException(
                    "Bạn không có quyền thực hiện thao tác: " + permission
            );
        }
    }
}
