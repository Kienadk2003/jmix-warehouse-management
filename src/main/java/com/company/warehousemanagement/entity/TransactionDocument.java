package com.company.warehousemanagement.entity;

import io.jmix.core.FileRef;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

@JmixEntity
@Entity
@Table(name = "TRANSACTION_DOCUMENT", indexes = @Index(name = "IDX_DOCUMENT_TRANSACTION", columnList = "TRANSACTION_ID"))
public class TransactionDocument extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TRANSACTION_ID", nullable = false)
    private WarehouseTransaction transaction;
    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;
    @Column(name = "CONTENT_TYPE", nullable = false, length = 100)
    private String contentType;
    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;
    @Column(name = "FILE_REF", nullable = false, length = 1024)
    private FileRef fileRef;

    public WarehouseTransaction getTransaction() { return transaction; }
    public void setTransaction(WarehouseTransaction value) { transaction = value; }
    public String getFileName() { return fileName; }
    public void setFileName(String value) { fileName = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { fileSize = value; }
    public FileRef getFileRef() { return fileRef; }
    public void setFileRef(FileRef value) { fileRef = value; }
}
