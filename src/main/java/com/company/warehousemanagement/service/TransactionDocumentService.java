package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.*;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.*;
import io.jmix.core.*;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.util.*;

@Service
public class TransactionDocumentService {
    private final DataManager data;
    private final UnconstrainedDataManager writer;
    private final FileStorage storage;
    private final WarehouseAuthorizationService authorization;
    private final DocumentValidator validator;

    public TransactionDocumentService(DataManager data, UnconstrainedDataManager writer,
            FileStorageLocator locator, WarehouseAuthorizationService authorization, DocumentValidator validator) {
        this.data = data;
        this.writer = writer;
        this.storage = locator.getDefault();
        this.authorization = authorization;
        this.validator = validator;
    }

    public List<TransactionDocument> list(UUID transactionId) {
        readableTransaction(transactionId);
        return data.load(TransactionDocument.class)
                .query("select d from TransactionDocument d where d.transaction.id = ?1 order by d.createdDate, d.fileName", transactionId)
                .fetchPlan(FetchPlan.BASE).list();
    }

    private WarehouseTransaction readableTransaction(UUID id) {
        authorization.require(WarehousePermissions.VIEW_DOCUMENTS, "Bạn không có quyền xem/tải chứng từ.");
        return data.load(WarehouseTransaction.class).id(id).optional()
                .orElseThrow(() -> new WarehouseBusinessException("Phiếu không tồn tại hoặc không thuộc phạm vi được phép."));
    }

    @Transactional
    public TransactionDocument upload(UUID transactionId, String name, InputStream input) throws IOException {
        readableTransaction(transactionId);
        authorization.require(WarehousePermissions.EDIT_DRAFT, "Bạn không có quyền tải chứng từ lên.");
        WarehouseTransaction transaction = data.load(WarehouseTransaction.class).id(transactionId)
                .lockMode(LockModeType.PESSIMISTIC_WRITE).one();
        if (transaction.getStatus() != WarehouseTransactionStatus.DRAFT) {
            throw new WarehouseBusinessException("Chỉ được thêm chứng từ khi phiếu ở trạng thái nháp.");
        }
        byte[] bytes = input.readNBytes(DocumentValidator.MAX_BYTES + 1);
        String contentType = validator.validate(name, bytes);
        FileRef ref = storage.saveStream(name, new ByteArrayInputStream(bytes));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.removeFile(ref);
            }
        });
        TransactionDocument document = data.create(TransactionDocument.class);
        document.setTransaction(transaction);
        document.setFileName(name);
        document.setContentType(contentType);
        document.setFileSize((long) bytes.length);
        document.setFileRef(ref);
        // Metadata is read-only to UI roles; all writes pass the parent authorization and status check above.
        return writer.save(document);
    }

    public Download download(UUID documentId) {
        authorization.require(WarehousePermissions.VIEW_DOCUMENTS, "Bạn không có quyền xem/tải chứng từ.");
        TransactionDocument document = data.load(TransactionDocument.class).id(documentId)
                .fetchPlan(p -> p.addFetchPlan(FetchPlan.BASE).add("transaction", FetchPlan.BASE))
                .optional().orElseThrow(() -> new WarehouseBusinessException("Không tìm thấy chứng từ hoặc không có quyền truy cập."));
        readableTransaction(document.getTransaction().getId());
        try (InputStream input = storage.openStream(document.getFileRef())) {
            return new Download(document.getFileName(), document.getContentType(), input.readNBytes(DocumentValidator.MAX_BYTES + 1));
        } catch (IOException e) {
            throw new WarehouseBusinessException("Không đọc được chứng từ. Vui lòng thử lại.");
        }
    }

    public record Download(String fileName, String contentType, byte[] bytes) { }
}
