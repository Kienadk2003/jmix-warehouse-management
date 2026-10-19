package com.company.warehousemanagement.view.adjustment;

import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.service.AdjustmentIssueService;
import com.company.warehousemanagement.service.AdjustmentPostingService;
import com.company.warehousemanagement.service.ReversalService;
import com.company.warehousemanagement.service.WarehouseTransactionWorkflowService;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.editor.DataGridEditor;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionPropertyContainer;
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
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "adjustments/:id", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.adjustmentDetail")
@ViewDescriptor(path = "adjustment-detail-view.xml")
@EditedEntityContainer("warehouseTransactionDc")
public class AdjustmentDetailView extends StandardDetailView<WarehouseTransaction> {

    @Autowired
    private Messages messages;

    @Autowired
    private com.company.warehousemanagement.view.document.TransactionDocumentsPanel documentsPanel;
    @ViewComponent
    private com.vaadin.flow.component.orderedlayout.VerticalLayout documentsHost;

    @Autowired
    private AdjustmentIssueService adjustmentIssueService;

    @Autowired
    private AdjustmentPostingService adjustmentPostingService;

    @Autowired
    private ReversalService reversalService;

    @Autowired
    private WarehouseTransactionWorkflowService workflowService;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @Autowired
    private DataManager dataManager;

    @ViewComponent
    private JmixButton submitButton;

    @ViewComponent
    private JmixButton approveButton;

    @ViewComponent
    private JmixButton rejectButton;

    @ViewComponent
    private JmixButton postButton;

    @ViewComponent
    private JmixButton saveDraftButton;

    @ViewComponent
    private JmixButton reverseButton;

    @ViewComponent
    private JmixButton addItemButton;

    @ViewComponent
    private JmixButton editItemButton;

    @ViewComponent
    private JmixButton removeItemButton;

    @ViewComponent
    private DataGrid<TransactionItem> itemsDataGrid;

    @ViewComponent
    private CollectionPropertyContainer<TransactionItem> itemsDc;

    @ViewComponent
    private TypedTextField<String> statusField;

    private boolean workflowActionInProgress;

    @Subscribe
    public void onInitEntity(InitEntityEvent<WarehouseTransaction> event) {
        adjustmentIssueService.prepareNewDraft(event.getEntity());
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
            adjustmentIssueService.validateDraft(transaction);
        } catch (WarehouseBusinessException exception) {
            event.getErrors().add(exception.getMessage());
        }
    }

    @Subscribe
    public void onAfterSave(AfterSaveEvent event) {
        refreshActions();
        if (!workflowActionInProgress) {
            Notification.show("Đã lưu phiếu điều chỉnh", 2500, Position.TOP_END);
        }
    }

    @Subscribe(id = "submitButton", subject = "clickListener")
    public void onSubmitButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null || entityStates.isNew(current)) {
            showError("Hãy lưu phiếu điều chỉnh trước khi gửi duyệt");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.DRAFT) {
            showError("Chỉ phiếu DRAFT mới được gửi duyệt");
            return;
        }

        workflowActionInProgress = true;
        submitButton.setEnabled(false);
        setShowSaveNotification(false);
        save()
                .then(() -> {
                    setShowSaveNotification(true);
                    submitSavedAdjustment();
                })
                .otherwise(() -> {
                    workflowActionInProgress = false;
                    setShowSaveNotification(true);
                    refreshActions();
                });
    }

    private void submitSavedAdjustment() {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            workflowActionInProgress = false;
            refreshActions();
            showError("Không tìm thấy phiếu điều chỉnh đã lưu");
            return;
        }

        try {
            WarehouseTransaction submitted = workflowService.submit(current.getId());
            current.setStatus(submitted.getStatus());
            refreshActions();
            clearChanges();
            Notification.show("Đã gửi phiếu điều chỉnh để chờ duyệt", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        } finally {
            workflowActionInProgress = false;
            refreshActions();
        }
    }

    @Subscribe(id = "approveButton", subject = "clickListener")
    public void onApproveButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            showError("Không tìm thấy phiếu điều chỉnh");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            showError("Chỉ phiếu đang chờ duyệt mới được duyệt");
            return;
        }

        try {
            WarehouseTransaction approved = workflowService.approve(current.getId());
            current.setStatus(approved.getStatus());
            refreshActions();
            clearChanges();
            Notification.show(
                    "Đã duyệt phiếu điều chỉnh. Có thể ghi sổ để cập nhật tồn.",
                    3000,
                    Position.TOP_END
            );
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        }
    }

    @Subscribe(id = "rejectButton", subject = "clickListener")
    public void onRejectButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            showError("Không tìm thấy phiếu điều chỉnh");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            showError("Chỉ phiếu đang chờ duyệt mới được từ chối");
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Từ chối phiếu điều chỉnh");
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
                showError("Vui lòng nhập lý do từ chối.");
                return;
            }

            confirmButton.setEnabled(false);
            try {
                WarehouseTransaction rejected = workflowService.reject(current.getId(), reason);
                current.setReason(rejected.getReason());
                current.setStatus(rejected.getStatus());
                dialog.close();
                refreshActions();
                clearChanges();
                Notification.show("Đã từ chối phiếu điều chỉnh", 3000, Position.TOP_END);
            } catch (WarehouseBusinessException exception) {
                confirmButton.setEnabled(true);
                showError(exception.getMessage());
            }
        });

        dialog.add(reasonField);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    @Subscribe(id = "postButton", subject = "clickListener")
    public void onPostButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null || entityStates.isNew(current)) {
            showError("Phiếu điều chỉnh chưa được lưu");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.APPROVED) {
            showError("Chỉ phiếu APPROVED mới được ghi sổ");
            return;
        }

        postButton.setEnabled(false);
        try {
            WarehouseTransaction posted = adjustmentPostingService.postAdjustment(current.getId());
            current.setStatus(posted.getStatus());
            current.setPostedAt(posted.getPostedAt());
            current.setPostedBy(posted.getPostedBy());
            refreshActions();
            // POST đã lưu ở service; reset trạng thái dirty của detail view để Đóng không hỏi lưu.
            clearChanges();
            Notification.show("Đã ghi sổ điều chỉnh tồn kho", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException exception) {
            postButton.setEnabled(true);
            showError(exception.getMessage());
        }
    }

    @Subscribe(id = "reverseButton", subject = "clickListener")
    public void onReverseButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null || entityStates.isNew(current)) {
            showError("Phiếu điều chỉnh chưa được lưu");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.POSTED) {
            showError("Chỉ phiếu POSTED mới được phép đảo phiếu");
            return;
        }
        if (current.getReversalOf() != null) {
            showError("Phiếu đảo không thể tiếp tục đảo lần nữa");
            return;
        }
        if (!authorizationService.isAllowed(WarehousePermissions.REVERSE)) {
            showError("Bạn không có quyền đảo phiếu");
            return;
        }

        reverseButton.setEnabled(false);
        try {
            WarehouseTransaction reversal = reversalService.reverse(current.getId());
            current.setStatus(WarehouseTransactionStatus.REVERSED);
            refreshActions();
            clearChanges();
            Notification.show(
                    "Đã đảo phiếu điều chỉnh. Phiếu đảo: " + reversal.getDocumentNo(),
                    4500,
                    Position.TOP_END
            );
        } catch (WarehouseBusinessException exception) {
            reverseButton.setEnabled(true);
            showError(exception.getMessage());
        } catch (Exception exception) {
            reverseButton.setEnabled(true);
            showError("Không thể đảo phiếu điều chỉnh. Hãy kiểm tra log ứng dụng.");
        }
    }

    @Subscribe(id = "addItemButton", subject = "clickListener")
    public void onAddItemButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null || transaction.getStatus() != WarehouseTransactionStatus.DRAFT) {
            showError("Chỉ có thể thêm dòng khi phiếu còn ở trạng thái DRAFT");
            return;
        }

        TransactionItem item = dataManager.create(TransactionItem.class);
        item.setTransaction(transaction);
        item.setLineNo(itemsDc.getItems().size() + 1);
        itemsDc.getMutableItems().add(item);

        DataGridEditor<TransactionItem> editor = itemsDataGrid.getEditor();
        editor.editItem(item);
    }

    @Subscribe(id = "editItemButton", subject = "clickListener")
    public void onEditItemButtonClick(ClickEvent<JmixButton> event) {
        TransactionItem selectedItem = itemsDataGrid.getSingleSelectedItem();
        if (selectedItem == null) {
            showError("Vui lòng chọn một dòng item");
            return;
        }
        if (itemsDataGrid.getEditor().isOpen()) {
            return;
        }
        itemsDataGrid.getEditor().editItem(selectedItem);
    }

    @Subscribe(id = "removeItemButton", subject = "clickListener")
    public void onRemoveItemButtonClick(ClickEvent<JmixButton> event) {
        TransactionItem selectedItem = itemsDataGrid.getSingleSelectedItem();
        if (selectedItem == null) {
            showError("Vui lòng chọn một dòng item");
            return;
        }
        if (itemsDataGrid.getEditor().isOpen()) {
            itemsDataGrid.getEditor().cancel();
        }
        itemsDc.getMutableItems().remove(selectedItem);
        renumberItems();
    }

    private void renumberItems() {
        int lineNo = 1;
        for (TransactionItem item : itemsDc.getMutableItems()) {
            item.setLineNo(lineNo++);
        }
    }

    private void refreshActions() {
        documentsPanel.show(documentsHost, getEditedEntity());
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null) {
            return;
        }

        boolean saved = !entityStates.isNew(transaction);
        WarehouseTransactionStatus status = transaction.getStatus();
        boolean draft = status == WarehouseTransactionStatus.DRAFT;
        boolean pending = status == WarehouseTransactionStatus.PENDING_APPROVAL;
        boolean approved = status == WarehouseTransactionStatus.APPROVED;
        boolean editable = draft && authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT);

        boolean canSubmit = draft && authorizationService.isAllowed(WarehousePermissions.SUBMIT);
        boolean canApprove = pending && authorizationService.isAllowed(WarehousePermissions.APPROVE);
        boolean canReject = pending && authorizationService.isAllowed(WarehousePermissions.REJECT);
        boolean canPost = approved && authorizationService.isAllowed(WarehousePermissions.POST);
        boolean canReverse = saved
                && status == WarehouseTransactionStatus.POSTED
                && transaction.getReversalOf() == null
                && authorizationService.isAllowed(WarehousePermissions.REVERSE);

        setReadOnly(!editable);
        itemsDataGrid.setEnabled(editable);
        saveDraftButton.setVisible(editable);
        saveDraftButton.setEnabled(editable);
        addItemButton.setVisible(editable);
        editItemButton.setVisible(editable);
        removeItemButton.setVisible(editable);

        submitButton.setVisible(saved && canSubmit);
        submitButton.setEnabled(saved && canSubmit && !workflowActionInProgress);
        approveButton.setVisible(saved && canApprove);
        approveButton.setEnabled(saved && canApprove);
        rejectButton.setVisible(saved && canReject);
        rejectButton.setEnabled(saved && canReject);
        postButton.setVisible(saved && canPost);
        postButton.setEnabled(saved && canPost);
        reverseButton.setVisible(canReverse);
        reverseButton.setEnabled(canReverse);

        updateStatusField(transaction);
    }

    private void updateStatusField(WarehouseTransaction transaction) {
        if (transaction.getStatus() == null) {
            statusField.setTypedValue("—");
        } else {
            statusField.setTypedValue(messages.getMessage(
                    "com.company.warehousemanagement.entity/WarehouseTransactionStatus." + transaction.getStatus().name()));
        }
    }

    private void showError(String message) {
        Notification.show(message, 4500, Position.TOP_END);
    }
}
