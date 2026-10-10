package com.company.warehousemanagement.view.export;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.service.ExportIssueService;
import com.company.warehousemanagement.service.ExportPostingService;
import com.company.warehousemanagement.service.ReversalService;
import com.company.warehousemanagement.service.WarehouseTransactionWorkflowService;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
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
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "export-issues/:id", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.exportDetail")
@ViewDescriptor(path = "export-detail-view.xml")
@EditedEntityContainer("warehouseTransactionDc")
public class ExportDetailView
        extends StandardDetailView<WarehouseTransaction> {

    @Autowired
    private ExportIssueService exportIssueService;

    @Autowired
    private ExportPostingService exportPostingService;

    @Autowired
    private ReversalService reversalService;

    @Autowired
    private WarehouseTransactionWorkflowService workflowService;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private WarehouseAuthorizationService authorizationService;
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

    @ViewComponent
    private JmixButton postButton;

    @ViewComponent
    private JmixButton submitButton;

    @ViewComponent
    private JmixButton approveButton;

    @ViewComponent
    private JmixButton rejectButton;

    @Subscribe
    public void onInitEntity(
            final InitEntityEvent<WarehouseTransaction> event) {

        exportIssueService.prepareNewDraft(event.getEntity());
    }
    @Subscribe(id = "reverseButton", subject = "clickListener")
    public void onReverseButtonClick(
            ClickEvent<JmixButton> event) {

        WarehouseTransaction current =
                getEditedEntity();

        if (current == null) {
            Notification.show(
                    "Không tìm thấy phiếu xuất",
                    5000,
                    Position.TOP_END
            );
            return;
        }

        if (current.getId() == null
                || entityStates.isNew(current)) {

            Notification.show(
                    "Phiếu chưa được lưu",
                    5000,
                    Position.TOP_END
            );
            return;
        }

        if (current.getStatus()
                != WarehouseTransactionStatus.POSTED) {

            Notification.show(
                    "Chỉ phiếu POSTED mới được phép Reverse",
                    5000,
                    Position.TOP_END
            );
            return;
        }

        try {

            WarehouseTransaction reversal =
                    reversalService.reverse(
                            current.getId()
                    );

            current.setStatus(
                    WarehouseTransactionStatus.REVERSED
            );

            updateStatusLabel(current);

            setReadOnly(true);

            addItemButton.setVisible(false);
            editItemButton.setVisible(false);
            removeItemButton.setVisible(false);
            postButton.setVisible(false);
            reverseButton.setVisible(false);

            Notification.show(
                    "Reverse phiếu xuất thành công. "
                            + "Phiếu reversal: "
                            + reversal.getDocumentNo(),
                    5000,
                    Position.TOP_END
            );

        } catch (WarehouseBusinessException e) {

            Notification.show(
                    e.getMessage(),
                    5000,
                    Position.TOP_END
            );
        }
    }
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null) {
            return;
        }
        updateActionVisibility(transaction);
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {

        try {
            exportIssueService.validateDraft(
                    getEditedEntity()
            );
        } catch (WarehouseBusinessException e) {

            event.getErrors().add(
                    e.getMessage()
            );
        }
    }

    @Subscribe
    public void onAfterSave(final AfterSaveEvent event) {
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction != null) {
            updateActionVisibility(transaction);
        }
    }

    /**
     * Saves the draft before sending it for approval.
     * Inventory is not changed by submit.
     */
    @Subscribe("submitButton")
    public void onSubmitButtonClick(final ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getStatus() != WarehouseTransactionStatus.DRAFT) {
            Notification.show("Chỉ phiếu DRAFT mới được gửi duyệt", 5000, Position.TOP_END);
            return;
        }

        submitButton.setEnabled(false);
        setShowSaveNotification(false);

        save()
                .then(() -> {
                    setShowSaveNotification(true);
                    submitSavedExport();
                })
                .otherwise(() -> {
                    setShowSaveNotification(true);
                    submitButton.setEnabled(true);
                });
    }

    private void submitSavedExport() {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            submitButton.setEnabled(true);
            Notification.show("Không thể gửi duyệt khi phiếu chưa được lưu", 5000, Position.TOP_END);
            return;
        }

        try {
            workflowService.submit(current.getId());
            Notification.show("Đã gửi phiếu xuất để Manager duyệt", 3000, Position.TOP_END);
            getUI().ifPresent(ui -> ui.navigate(ExportListView.class));
        } catch (WarehouseBusinessException e) {
            submitButton.setEnabled(true);
            Notification.show(e.getMessage(), 5000, Position.TOP_END);
        }
    }

    @Subscribe("approveButton")
    public void onApproveButtonClick(final ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            Notification.show("Không tìm thấy phiếu xuất đã lưu", 5000, Position.TOP_END);
            return;
        }

        try {
            workflowService.approve(current.getId());
            Notification.show("Đã duyệt phiếu xuất. Hãy mở lại phiếu để ghi sổ", 4000, Position.TOP_END);
            getUI().ifPresent(ui -> ui.navigate(ExportListView.class));
        } catch (WarehouseBusinessException e) {
            Notification.show(e.getMessage(), 5000, Position.TOP_END);
        }
    }

    @Subscribe("rejectButton")
    public void onRejectButtonClick(final ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            Notification.show("Không tìm thấy phiếu xuất đã lưu", 5000, Position.TOP_END);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Từ chối phiếu xuất");

        TextArea rejectionReason = new TextArea("Lý do từ chối");
        rejectionReason.setWidthFull();
        rejectionReason.setMaxLength(1000);
        rejectionReason.setRequired(true);
        rejectionReason.setPlaceholder("Nhập lý do từ chối phiếu...");

        Button confirmButton = new Button("Xác nhận từ chối", click -> {
            String reason = rejectionReason.getValue();
            if (reason == null || reason.isBlank()) {
                rejectionReason.setInvalid(true);
                rejectionReason.setErrorMessage("Vui lòng nhập lý do từ chối");
                return;
            }

            try {
                workflowService.reject(current.getId(), reason.trim());
                dialog.close();
                Notification.show("Đã từ chối phiếu xuất", 3000, Position.TOP_END);
                getUI().ifPresent(ui -> ui.navigate(ExportListView.class));
            } catch (WarehouseBusinessException e) {
                Notification.show(e.getMessage(), 5000, Position.TOP_END);
            }
        });

        Button cancelButton = new Button("Hủy", click -> dialog.close());
        dialog.add(rejectionReason, new HorizontalLayout(confirmButton, cancelButton));
        dialog.setWidth("32em");
        dialog.open();
    }

    @Subscribe("postButton")
    public void onPostButtonClick(
            final ClickEvent<JmixButton> event) {

        WarehouseTransaction current =
                getEditedEntity();

        if (current == null) {

            Notification.show(
                    "Không tìm thấy phiếu xuất",
                    5000,
                    Position.TOP_END
            );

            return;
        }


        if (current.getStatus()
                != WarehouseTransactionStatus.APPROVED) {

            Notification.show(
                    "Chỉ phiếu APPROVED mới được phép POST",
                    5000,
                    Position.TOP_END
            );

            return;
        }

        // The approved transaction is read-only. Do not call save() here:
        // ExportIssueService.validateDraft() intentionally accepts DRAFT only.
        postButton.setEnabled(false);
        postSavedExport();
    }

    private void postSavedExport() {
        WarehouseTransaction current = getEditedEntity();

        if (current == null || current.getId() == null) {
            postButton.setEnabled(true);
            Notification.show(
                    "Không thể lưu phiếu xuất trước khi POST",
                    5000,
                    Position.TOP_END
            );
            return;
        }

        try {

            WarehouseTransaction posted =
                    exportPostingService.postExport(
                            current.getId()
                    );

            current.setStatus(
                    WarehouseTransactionStatus.POSTED
            );

            if (posted.getPostedAt() != null) {
                current.setPostedAt(
                        posted.getPostedAt()
                );
            }

            if (posted.getPostedBy() != null) {
                current.setPostedBy(
                        posted.getPostedBy()
                );
            }

            updateStatusLabel(current);


            setReadOnly(true);

            addItemButton.setVisible(false);
            editItemButton.setVisible(false);
            removeItemButton.setVisible(false);
            postButton.setVisible(false);

            Notification.show(
                    "POST phiếu xuất thành công",
                    3000,
                    Position.TOP_END
            );

            getUI().ifPresent(ui ->
                    ui.navigate(ExportListView.class)
            );

        } catch (WarehouseBusinessException e) {

            postButton.setEnabled(true);

            Notification.show(
                    e.getMessage(),
                    5000,
                    Position.TOP_END
            );
        }
    }

    private void updateActionVisibility(WarehouseTransaction transaction) {
        updateStatusLabel(transaction);

        boolean saved = !entityStates.isNew(transaction);
        WarehouseTransactionStatus status = transaction.getStatus();

        boolean editable = status == WarehouseTransactionStatus.DRAFT
                && authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT);
        boolean canSubmit = status == WarehouseTransactionStatus.DRAFT
                && authorizationService.isAllowed(WarehousePermissions.SUBMIT);
        boolean canApprove = saved
                && status == WarehouseTransactionStatus.PENDING_APPROVAL
                && authorizationService.isAllowed(WarehousePermissions.APPROVE);
        boolean canReject = saved
                && status == WarehouseTransactionStatus.PENDING_APPROVAL
                && authorizationService.isAllowed(WarehousePermissions.REJECT);
        boolean canPost = saved
                && status == WarehouseTransactionStatus.APPROVED
                && authorizationService.isAllowed(WarehousePermissions.POST);
        boolean canReverse = saved
                && status == WarehouseTransactionStatus.POSTED
                && transaction.getReversalOf() == null
                && authorizationService.isAllowed(WarehousePermissions.REVERSE);

        setReadOnly(!editable);
        documentNoField.setReadOnly(true);

        addItemButton.setVisible(editable);
        editItemButton.setVisible(editable);
        removeItemButton.setVisible(editable);

        submitButton.setVisible(canSubmit);
        approveButton.setVisible(canApprove);
        rejectButton.setVisible(canReject);
        postButton.setVisible(canPost);
        reverseButton.setVisible(canReverse);
    }

    private void updateStatusLabel(
            WarehouseTransaction transaction) {

        WarehouseTransactionStatus status =
                transaction.getStatus();

        statusLabel.setText(
                status != null
                        ? status.getId()
                        : "-"
        );
    }
}
