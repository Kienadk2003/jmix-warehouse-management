package com.company.warehousemanagement.view.document;

import com.company.warehousemanagement.entity.*;
import com.company.warehousemanagement.service.*;
import com.company.warehousemanagement.security.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import io.jmix.core.EntityStates;
import io.jmix.flowui.download.Downloader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** Creates a separate component for every view; no per-user state is kept in the singleton. */
@Component
public class TransactionDocumentsPanel {

    private final TransactionDocumentService service;
    private final WarehouseAuthorizationService authorization;
    private final EntityStates states;
    private final Downloader downloader;

    public TransactionDocumentsPanel(TransactionDocumentService service,
                                     WarehouseAuthorizationService authorization,
                                     EntityStates states,
                                     Downloader downloader) {
        this.service = service;
        this.authorization = authorization;
        this.states = states;
        this.downloader = downloader;
    }

    public void show(VerticalLayout host, WarehouseTransaction transaction) {
        host.removeAll();
        host.setPadding(false);
        host.add(new H3("Chứng từ đính kèm"));

        if (!authorization.isAllowed(WarehousePermissions.VIEW_DOCUMENTS)) {
            host.add(new Span("Bạn không có quyền xem chứng từ."));
            return;
        }

        if (transaction == null || states.isNew(transaction)) {
            host.add(new Span("Lưu phiếu nháp trước khi thêm chứng từ."));
            return;
        }

        var id = transaction.getId();
        Grid<TransactionDocument> grid = new Grid<>();
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addColumn(TransactionDocument::getFileName).setHeader("Tên file").setFlexGrow(2);
        grid.addColumn(d -> String.format("%.1f KB", d.getFileSize() / 1024.0)).setHeader("Dung lượng");
        grid.addColumn(TransactionDocument::getCreatedBy).setHeader("Người tải lên");
        grid.addColumn(TransactionDocument::getCreatedDate).setHeader("Thời điểm");
        grid.addComponentColumn(d -> new HorizontalLayout(
                new Button("Xem", e -> {
                    try {
                        service.download(d.getId()); // Authorize before opening, and again for the stream request.
                        Dialog dialog = new Dialog();
                        dialog.setHeaderTitle(d.getFileName());
                        dialog.setWidth("85vw");
                        dialog.setHeight("85vh");
                        IFrame frame = new IFrame(DownloadHandler.fromInputStream(event -> {
                            try {
                                var file = service.download(d.getId());
                                event.getResponse().setHeader("X-Content-Type-Options", "nosniff");
                                return new DownloadResponse(new ByteArrayInputStream(file.bytes()), file.fileName(),
                                        file.contentType(), file.bytes().length);
                            } catch (RuntimeException exception) {
                                return DownloadResponse.error(403);
                            }
                        }).inline());
                        frame.setSizeFull();
                        frame.setTitle(d.getFileName());
                        dialog.add(frame);
                        dialog.getFooter().add(new Button("Đóng", click -> dialog.close()));
                        dialog.open();
                    } catch (RuntimeException ex) {
                        Notification.show(ex.getMessage());
                    }
                }),
                new Button("Tải xuống", e -> {
                    try {
                        var file = service.download(d.getId());
                        downloader.download(file.bytes(), file.fileName());
                    } catch (RuntimeException ex) {
                        Notification.show(ex.getMessage());
                    }
                }))).setHeader("Thao tác").setFlexGrow(2);

        var documents = service.list(id);
        grid.setItems(documents);
        Span count = new Span(documents.isEmpty()
                ? "Chưa có chứng từ."
                : documents.size() + " chứng từ");
        host.add(count, grid);

        if (transaction.getStatus() == WarehouseTransactionStatus.DRAFT
                && authorization.isAllowed(WarehousePermissions.EDIT_DRAFT)) {
            // Server-side handler enforces the file-size limit even if the UI limit is bypassed.
            Upload upload = new Upload((com.vaadin.flow.server.streams.UploadHandler) event -> {
                try (var input = event.getInputStream()) {
                    service.upload(id, event.getFileName(), input);
                    event.getUI().accessSynchronously(() -> {
                        var updated = service.list(id);
                        grid.setItems(updated);
                        count.setText(updated.size() + " chứng từ");
                        Notification.show("Đã thêm chứng từ: " + event.getFileName());
                    });
                } catch (Exception ex) {
                    event.reject("Không thể thêm chứng từ");
                    event.getUI().accessSynchronously(() -> Notification.show(ex.getMessage()));
                }
            });
            upload.setMaxFileSize(DocumentValidator.MAX_BYTES);
            upload.setMaxFiles(20);
            upload.setAcceptedFileTypes(".pdf", ".png", ".jpg", ".jpeg");
            upload.setUploadButton(new Button("Chọn chứng từ"));
            upload.setDropLabel(new Span("Kéo thả một hoặc nhiều file"));
            upload.addFileRejectedListener(e -> Notification.show(e.getErrorMessage()));
            host.add(new Span("PDF, PNG, JPG • tối đa 10 MB/file • thêm chứng từ trước khi gửi duyệt"), upload);
        }
    }
}
