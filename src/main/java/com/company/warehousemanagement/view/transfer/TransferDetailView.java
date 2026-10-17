package com.company.warehousemanagement.view.transfer;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.service.ReversalService;
import com.company.warehousemanagement.service.TransferIssueService;
import com.company.warehousemanagement.service.TransferPostingService;
import com.company.warehousemanagement.service.WarehouseTransactionWorkflowService;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.core.Messages;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.StandardDetailView.AfterSaveEvent;
import io.jmix.flowui.view.StandardDetailView.InitEntityEvent;
import io.jmix.flowui.view.StandardDetailView.ValidationEvent;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "transfers/:id", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.transferDetail")
@ViewDescriptor(path = "transfer-detail-view.xml")
@EditedEntityContainer("warehouseTransactionDc")
public class TransferDetailView extends StandardDetailView<WarehouseTransaction> {

    private static final Logger log = LoggerFactory.getLogger(TransferDetailView.class);

    @Autowired
    private com.company.warehousemanagement.view.document.TransactionDocumentsPanel documentsPanel;
    @ViewComponent
    private com.vaadin.flow.component.orderedlayout.VerticalLayout documentsHost;

    @Autowired
    private TransferIssueService transferIssueService;

    @Autowired
    private TransferPostingService transferPostingService;

    @Autowired
    private WarehouseTransactionWorkflowService workflowService;

    @Autowired
    private ReversalService reversalService;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private Messages messages;

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @ViewComponent
    private JmixButton saveDraftButton;

    @ViewComponent
    private JmixButton submitButton;

    @ViewComponent
    private JmixButton approveButton;

    @ViewComponent
    private JmixButton rejectButton;

    @ViewComponent
    private JmixButton postButton;

    @ViewComponent
    private JmixButton reverseButton;

    @ViewComponent
    private Span statusLabel;

    @ViewComponent
    private TypedTextField<String> documentNoField;

    @ViewComponent
    private JmixButton addItemButton;

    @ViewComponent
    private JmixButton editItemButton;

    @ViewComponent
    private JmixButton removeItemButton;

    private boolean workflowActionInProgress;

    @Subscribe
    public void onInitEntity(InitEntityEvent<WarehouseTransaction> event) {
        transferIssueService.prepareNewDraft(event.getEntity());
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        refreshActions();
    }

    @Subscribe
    public void onValidation(ValidationEvent event) {
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null || transaction.getStatus() != WarehouseTransactionStatus.DRAFT) {
            return;
        }

        try {
            transferIssueService.validateDraft(transaction);
        } catch (WarehouseBusinessException e) {
            event.getErrors().add(e.getMessage());
        }
    }

    /** Save a draft and remain on this screen. */
    @Subscribe("saveDraftButton")
    public void onSaveDraftButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getStatus() != WarehouseTransactionStatus.DRAFT) {
            showError("Chỉ có thể lưu nháp khi phiếu ở trạng thái DRAFT.");
            return;
        }

        workflowActionInProgress = true;
        saveDraftButton.setEnabled(false);
        setShowSaveNotification(false);

        save()
                .then(() -> {
                    setShowSaveNotification(true);
                    workflowActionInProgress = false;
                    refreshActions();
                    Notification.show("Đã lưu nháp phiếu chuyển kho", 2500, Position.TOP_END);
                })
                .otherwise(() -> {
                    setShowSaveNotification(true);
                    workflowActionInProgress = false;
                    refreshActions();
                });
    }

    @Subscribe(id = "submitButton", subject = "clickListener")
    public void onSubmitButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getStatus() != WarehouseTransactionStatus.DRAFT) {
            showError("Chỉ phiếu DRAFT mới được gửi duyệt.");
            return;
        }

        workflowActionInProgress = true;
        submitButton.setEnabled(false);
        setShowSaveNotification(false);

        // Persist edits first; save() keeps the current detail view open.
        save()
                .then(() -> {
                    setShowSaveNotification(true);
                    submitSavedTransfer();
                })
                .otherwise(() -> {
                    workflowActionInProgress = false;
                    setShowSaveNotification(true);
                    refreshActions();
                });
    }

    private void submitSavedTransfer() {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            workflowActionInProgress = false;
            refreshActions();
            showError("Không tìm thấy phiếu chuyển kho đã lưu.");
            return;
        }

        try {
            WarehouseTransaction submitted = workflowService.submit(current.getId());
            current.setStatus(submitted.getStatus());
            clearChanges();
            workflowActionInProgress = false;
            refreshActions();
            Notification.show("Đã gửi phiếu chuyển kho để chờ duyệt", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException e) {
            workflowActionInProgress = false;
            refreshActions();
            log.warn("Business error while submitting transfer id={}", current.getId(), e);
            showError(e.getMessage());
        } catch (Exception e) {
            workflowActionInProgress = false;
            refreshActions();
            log.error("Unexpected error while submitting transfer id={}", current.getId(), e);
            showError("Không thể gửi duyệt phiếu chuyển kho. Hãy kiểm tra log ứng dụng.");
        }
    }

    @Subscribe(id = "approveButton", subject = "clickListener")
    public void onApproveButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            showError("Không tìm thấy phiếu chuyển kho đã lưu.");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            showError("Chỉ phiếu đang chờ duyệt mới được duyệt.");
            return;
        }

        approveButton.setEnabled(false);
        try {
            WarehouseTransaction approved = workflowService.approve(current.getId());
            current.setStatus(approved.getStatus());
            clearChanges();
            refreshActions();
            Notification.show("Đã duyệt phiếu chuyển kho. Bạn có thể POST ngay tại đây.", 3500, Position.TOP_END);
        } catch (WarehouseBusinessException e) {
            log.warn("Business error while approving transfer id={}", current.getId(), e);
            refreshActions();
            showError(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while approving transfer id={}", current.getId(), e);
            refreshActions();
            showError("Duyệt phiếu chuyển kho thất bại. Hãy kiểm tra log ứng dụng.");
        }
    }

    @Subscribe(id = "rejectButton", subject = "clickListener")
    public void onRejectButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            showError("Không tìm thấy phiếu chuyển kho đã lưu.");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            showError("Chỉ phiếu đang chờ duyệt mới được từ chối.");
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Từ chối phiếu chuyển kho");
        dialog.setWidth("450px");

        TextArea reasonField = new TextArea("Lý do từ chối");
        reasonField.setWidthFull();
        reasonField.setRequired(true);
        reasonField.setMaxLength(1000);
        reasonField.setPlaceholder("Nhập lý do từ chối...");

        Button cancelButton = new Button("Hủy", e -> dialog.close());
        Button confirmButton = new Button("Xác nhận từ chối");
        confirmButton.addClickListener(e -> {
            String reason = reasonField.getValue();
            if (reason == null || reason.isBlank()) {
                reasonField.setInvalid(true);
                reasonField.setErrorMessage("Vui lòng nhập lý do từ chối.");
                return;
            }

            confirmButton.setEnabled(false);
            try {
                WarehouseTransaction rejected = workflowService.reject(current.getId(), reason.trim());
                current.setReason(rejected.getReason());
                current.setStatus(rejected.getStatus());
                dialog.close();
                clearChanges();
                refreshActions();
                Notification.show("Đã từ chối phiếu chuyển kho", 3000, Position.TOP_END);
            } catch (WarehouseBusinessException ex) {
                confirmButton.setEnabled(true);
                log.warn("Business error while rejecting transfer id={}", current.getId(), ex);
                showError(ex.getMessage());
            } catch (Exception ex) {
                confirmButton.setEnabled(true);
                log.error("Unexpected error while rejecting transfer id={}", current.getId(), ex);
                showError("Không thể từ chối phiếu chuyển kho. Hãy kiểm tra log ứng dụng.");
            }
        });

        dialog.add(reasonField);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    @Subscribe(id = "postButton", subject = "clickListener")
    public void onPostButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            showError("Không tìm thấy phiếu chuyển kho đã lưu.");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.APPROVED) {
            showError("Chỉ phiếu APPROVED mới được POST.");
            return;
        }

        postButton.setEnabled(false);
        postSavedTransfer();
    }

    private void postSavedTransfer() {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            refreshActions();
            showError("Không thể POST khi phiếu chuyển kho chưa được lưu.");
            return;
        }

        try {
            WarehouseTransaction posted = transferPostingService.postTransfer(current.getId());
            current.setStatus(WarehouseTransactionStatus.POSTED);
            current.setPostedAt(posted.getPostedAt());
            current.setPostedBy(posted.getPostedBy());
            clearChanges();
            refreshActions();
            Notification.show("POST phiếu chuyển kho thành công. Tồn kho hai kho đã được cập nhật.", 3500, Position.TOP_END);
            // Deliberately stay on this detail screen, matching the export workflow.
        } catch (WarehouseBusinessException e) {
            log.warn("Business error while posting transfer id={}", current.getId(), e);
            refreshActions();
            showError(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected/access-control error while posting transfer id={}", current.getId(), e);
            refreshActions();
            showError("POST phiếu chuyển kho thất bại. Hãy kiểm tra log ứng dụng.");
        }
    }

    @Subscribe(id = "reverseButton", subject = "clickListener")
    public void onReverseButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null || entityStates.isNew(current)) {
            showError("Không tìm thấy phiếu chuyển kho đã lưu.");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.POSTED) {
            showError("Chỉ phiếu POSTED mới được phép đảo.");
            return;
        }

        try {
            WarehouseTransaction reversal = reversalService.reverse(current.getId());
            current.setStatus(WarehouseTransactionStatus.REVERSED);
            clearChanges();
            refreshActions();
            Notification.show("Đảo phiếu chuyển kho thành công. Phiếu đảo: " + reversal.getDocumentNo(), 4500, Position.TOP_END);
        } catch (WarehouseBusinessException e) {
            log.warn("Business error while reversing transfer id={}", current.getId(), e);
            showError(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while reversing transfer id={}", current.getId(), e);
            showError("Không thể đảo phiếu chuyển kho. Hãy kiểm tra log ứng dụng.");
        }
    }

    @Subscribe
    public void onAfterSave(AfterSaveEvent event) {
        refreshActions();
        if (!workflowActionInProgress && getEditedEntity() != null
                && getEditedEntity().getStatus() == WarehouseTransactionStatus.DRAFT) {
            Notification.show("Đã lưu phiếu chuyển kho", 2500, Position.TOP_END);
        }
    }

    private void refreshActions() {
        documentsPanel.show(documentsHost, getEditedEntity());
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null) {
            return;
        }

        boolean saved = transaction.getId() != null && !entityStates.isNew(transaction);
        WarehouseTransactionStatus status = transaction.getStatus();
        boolean draft = status == WarehouseTransactionStatus.DRAFT;
        boolean pending = status == WarehouseTransactionStatus.PENDING_APPROVAL;
        boolean approved = status == WarehouseTransactionStatus.APPROVED;
        boolean posted = status == WarehouseTransactionStatus.POSTED;

        boolean editable = draft && authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT);
        boolean canSubmit = draft && authorizationService.isAllowed(WarehousePermissions.SUBMIT);
        boolean canApprove = pending && authorizationService.isAllowed(WarehousePermissions.APPROVE);
        boolean canReject = pending && authorizationService.isAllowed(WarehousePermissions.REJECT);
        boolean canPost = approved && authorizationService.isAllowed(WarehousePermissions.POST);
        boolean canReverse = saved && posted && transaction.getReversalOf() == null
                && authorizationService.isAllowed(WarehousePermissions.REVERSE);

        setReadOnly(!editable);
        documentNoField.setReadOnly(true);

        saveDraftButton.setVisible(editable);
        saveDraftButton.setEnabled(editable && !workflowActionInProgress);
        addItemButton.setVisible(editable);
        editItemButton.setVisible(editable);
        removeItemButton.setVisible(editable);

        submitButton.setVisible(saved && canSubmit);
        submitButton.setEnabled(saved && canSubmit && !workflowActionInProgress);
        approveButton.setVisible(saved && canApprove);
        approveButton.setEnabled(saved && canApprove && !workflowActionInProgress);
        rejectButton.setVisible(saved && canReject);
        rejectButton.setEnabled(saved && canReject && !workflowActionInProgress);
        postButton.setVisible(saved && canPost);
        postButton.setEnabled(saved && canPost && !workflowActionInProgress);
        reverseButton.setVisible(canReverse);
        reverseButton.setEnabled(canReverse && !workflowActionInProgress);

        updateStatusLabel(transaction);
    }

    private void updateStatusLabel(WarehouseTransaction transaction) {
        WarehouseTransactionStatus status = transaction.getStatus();
        statusLabel.setText(status == null ? "—" : messages.getMessage(
                "com.company.warehousemanagement.entity/WarehouseTransactionStatus." + status.name()));
    }

    private void showError(String message) {
        Notification.show(message, 4500, Position.TOP_END);
    }
}
