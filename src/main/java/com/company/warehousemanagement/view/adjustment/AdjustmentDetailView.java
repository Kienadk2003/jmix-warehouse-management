package com.company.warehousemanagement.view.adjustment;

import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.service.AdjustmentIssueService;
import com.company.warehousemanagement.service.AdjustmentPostingService;
import com.company.warehousemanagement.service.WarehouseTransactionWorkflowService;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.router.Route;
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
    private AdjustmentIssueService adjustmentIssueService;

    @Autowired
    private AdjustmentPostingService adjustmentPostingService;

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
            Notification.show("Ä�Ã£ lÆ°u phiáº¿u Ä‘iá»�u chá»‰nh", 2500, Position.TOP_END);
        }
    }

    @Subscribe(id = "submitButton", subject = "clickListener")
    public void onSubmitButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null || entityStates.isNew(current)) {
            showError("HÃ£y lÆ°u phiáº¿u Ä‘iá»�u chá»‰nh trÆ°á»›c khi gá»­i duyá»‡t");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.DRAFT) {
            showError("Chá»‰ phiáº¿u DRAFT má»›i Ä‘Æ°á»£c gá»­i duyá»‡t");
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
            showError("KhÃ´ng tÃ¬m tháº¥y phiáº¿u Ä‘iá»�u chá»‰nh Ä‘Ã£ lÆ°u");
            return;
        }

        try {
            WarehouseTransaction submitted = workflowService.submit(current.getId());
            current.setStatus(submitted.getStatus());
            Notification.show("Ä�Ã£ gá»­i phiáº¿u Ä‘iá»�u chá»‰nh Ä‘á»ƒ chá»� duyá»‡t", 3000, Position.TOP_END);
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
            showError("KhÃ´ng tÃ¬m tháº¥y phiáº¿u Ä‘iá»�u chá»‰nh");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            showError("Chá»‰ phiáº¿u Ä‘ang chá»� duyá»‡t má»›i Ä‘Æ°á»£c duyá»‡t");
            return;
        }

        try {
            WarehouseTransaction approved = workflowService.approve(current.getId());
            current.setStatus(approved.getStatus());
            refreshActions();
            Notification.show("Ä�Ã£ duyá»‡t phiáº¿u Ä‘iá»�u chá»‰nh. CÃ³ thá»ƒ ghi sá»• Ä‘á»ƒ cáº­p nháº­t tá»“n.",
                    3000, Position.TOP_END);
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        }
    }

    @Subscribe(id = "rejectButton", subject = "clickListener")
    public void onRejectButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            showError("KhÃ´ng tÃ¬m tháº¥y phiáº¿u Ä‘iá»�u chá»‰nh");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            showError("Chá»‰ phiáº¿u Ä‘ang chá»� duyá»‡t má»›i Ä‘Æ°á»£c tá»« chá»‘i");
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Tá»« chá»‘i phiáº¿u Ä‘iá»�u chá»‰nh");
        dialog.setWidth("450px");

        TextArea reasonField = new TextArea("LÃ½ do tá»« chá»‘i");
        reasonField.setWidthFull();
        reasonField.setRequired(true);
        reasonField.setMaxLength(1000);
        reasonField.setPlaceholder("Nháº­p lÃ½ do tá»« chá»‘i...");

        Button cancelButton = new Button("Há»§y", e -> dialog.close());
        Button confirmButton = new Button("XÃ¡c nháº­n tá»« chá»‘i");
        confirmButton.addClickListener(e -> {
            String reason = reasonField.getValue();
            if (reason == null || reason.isBlank()) {
                showError("Vui lÃ²ng nháº­p lÃ½ do tá»« chá»‘i.");
                return;
            }

            confirmButton.setEnabled(false);
            try {
                WarehouseTransaction rejected =
                        workflowService.reject(current.getId(), reason);
                current.setReason(rejected.getReason());
                current.setStatus(rejected.getStatus());
                dialog.close();
                refreshActions();
                Notification.show("Ä�Ã£ tá»« chá»‘i phiáº¿u Ä‘iá»�u chá»‰nh", 3000, Position.TOP_END);
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
            showError("Phiáº¿u Ä‘iá»�u chá»‰nh chÆ°a Ä‘Æ°á»£c lÆ°u");
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.APPROVED) {
            showError("Chá»‰ phiáº¿u APPROVED má»›i Ä‘Æ°á»£c ghi sá»•");
            return;
        }

        postButton.setEnabled(false);
        try {
            WarehouseTransaction posted = adjustmentPostingService.postAdjustment(current.getId());
            current.setStatus(posted.getStatus());
            current.setPostedAt(posted.getPostedAt());
            current.setPostedBy(posted.getPostedBy());
            refreshActions();
            Notification.show("Ä�Ã£ ghi sá»• Ä‘iá»�u chá»‰nh tá»“n kho", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException exception) {
            postButton.setEnabled(true);
            showError(exception.getMessage());
        }
    }

    @Subscribe(id = "addItemButton", subject = "clickListener")
    public void onAddItemButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null || transaction.getStatus() != WarehouseTransactionStatus.DRAFT) {
            showError("Chá»‰ cÃ³ thá»ƒ thÃªm dÃ²ng khi phiáº¿u cÃ²n á»Ÿ tráº¡ng thÃ¡i DRAFT");
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
            showError("Vui lÃ²ng chá»�n má»™t dÃ²ng item");
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
            showError("Vui lÃ²ng chá»�n má»™t dÃ²ng item");
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

        setReadOnly(!editable);
        itemsDataGrid.setEnabled(editable);
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

        updateStatusField(transaction);
    }

    private void updateStatusField(WarehouseTransaction transaction) {
        if (transaction.getStatus() == null) {
            statusField.setTypedValue("-");
        } else {
            statusField.setTypedValue(transaction.getStatus().getId());
        }
    }

    private void showError(String message) {
        Notification.show(message, 4500, Position.TOP_END);
    }
}
