package com.company.warehousemanagement.view.transfer;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.service.ReversalService;
import com.company.warehousemanagement.service.TransferIssueService;
import com.company.warehousemanagement.service.TransferPostingService;
import com.company.warehousemanagement.service.WarehouseTransactionWorkflowService;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
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

@Route(
        value = "transfers/:id",
        layout = MainView.class
)
@ViewController(
        id = "WarehouseTransaction.transferDetail"
)
@ViewDescriptor(
        path = "transfer-detail-view.xml"
)
@EditedEntityContainer("warehouseTransactionDc")
public class TransferDetailView
        extends StandardDetailView<WarehouseTransaction> {

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
    private WarehouseAuthorizationService authorizationService;

    @ViewComponent
    private JmixButton submitButton;

    @ViewComponent
    private JmixButton approveButton;

    @ViewComponent
    private JmixButton rejectButton;

    @ViewComponent
    private JmixButton postButton;

    private boolean workflowActionInProgress;

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


    /*
     * ============================================================
     * INIT ENTITY
     * ============================================================
     */

    @Subscribe
    public void onInitEntity(
            final InitEntityEvent<WarehouseTransaction> event) {

        transferIssueService.prepareNewDraft(
                event.getEntity()
        );
    }


    /*
     * ============================================================
     * BEFORE SHOW
     * ============================================================
     */

    @Subscribe
    public void onBeforeShow(
            final BeforeShowEvent event) {
        refreshActions();
    }


    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */

    @Subscribe
    public void onValidation(
            final ValidationEvent event) {

        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null
                || transaction.getStatus() != WarehouseTransactionStatus.DRAFT) {
            return;
        }

        try {
            transferIssueService.validateDraft(transaction);
        } catch (WarehouseBusinessException e) {
            event.getErrors().add(e.getMessage());
        }
    }


    /*
     * ============================================================
     * SUBMIT / APPROVE / REJECT
     * ============================================================
     */

    @Subscribe(
            id = "submitButton",
            subject = "clickListener"
    )
    public void onSubmitButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || entityStates.isNew(current) || current.getId() == null) {
            Notification.show("LÃ†Â°u phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho trÃ†Â°Ã¡Â»â€ºc khi gÃ¡Â»Â­i duyÃ¡Â»â€¡t", 4500, Position.TOP_END);
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.DRAFT) {
            Notification.show("ChÃ¡Â»â€° phiÃ¡ÂºÂ¿u DRAFT mÃ¡Â»â€ºi Ã„â€˜Ã†Â°Ã¡Â»Â£c gÃ¡Â»Â­i duyÃ¡Â»â€¡t", 4500, Position.TOP_END);
            return;
        }

        workflowActionInProgress = true;
        submitButton.setEnabled(false);
        setShowSaveNotification(false);
        save()
                .then(() -> {
                    setShowSaveNotification(true);
                    submitSavedTransfer();
                })
                .otherwise(() -> {
                    workflowActionInProgress = false;
                    setShowSaveNotification(true);
                    submitButton.setEnabled(true);
                });
    }

    private void submitSavedTransfer() {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            workflowActionInProgress = false;
            submitButton.setEnabled(true);
            Notification.show("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho Ã„â€˜ÃƒÂ£ lÃ†Â°u", 4500, Position.TOP_END);
            return;
        }

        try {
            WarehouseTransaction submitted = workflowService.submit(current.getId());
            current.setStatus(submitted.getStatus());
            workflowActionInProgress = false;
            refreshActions();
            Notification.show("Ã„ï¿½ÃƒÂ£ gÃ¡Â»Â­i phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho Ã„â€˜Ã¡Â»Æ’ chÃ¡Â»ï¿½ duyÃ¡Â»â€¡t", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException e) {
            workflowActionInProgress = false;
            refreshActions();
            Notification.show(e.getMessage(), 4500, Position.TOP_END);
        }
    }

    @Subscribe(
            id = "approveButton",
            subject = "clickListener"
    )
    public void onApproveButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            Notification.show("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho", 4500, Position.TOP_END);
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            Notification.show("ChÃ¡Â»â€° phiÃ¡ÂºÂ¿u Ã„â€˜ang chÃ¡Â»ï¿½ duyÃ¡Â»â€¡t mÃ¡Â»â€ºi Ã„â€˜Ã†Â°Ã¡Â»Â£c duyÃ¡Â»â€¡t", 4500, Position.TOP_END);
            return;
        }

        try {
            WarehouseTransaction approved = workflowService.approve(current.getId());
            current.setStatus(approved.getStatus());
            refreshActions();
            Notification.show("Ã„ï¿½ÃƒÂ£ duyÃ¡Â»â€¡t phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException e) {
            Notification.show(e.getMessage(), 4500, Position.TOP_END);
        }
    }

    @Subscribe(
            id = "rejectButton",
            subject = "clickListener"
    )
    public void onRejectButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            Notification.show("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho", 4500, Position.TOP_END);
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.PENDING_APPROVAL) {
            Notification.show("ChÃ¡Â»â€° phiÃ¡ÂºÂ¿u Ã„â€˜ang chÃ¡Â»ï¿½ duyÃ¡Â»â€¡t mÃ¡Â»â€ºi Ã„â€˜Ã†Â°Ã¡Â»Â£c tÃ¡Â»Â« chÃ¡Â»â€˜i", 4500, Position.TOP_END);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("TÃ¡Â»Â« chÃ¡Â»â€˜i phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho");
        dialog.setWidth("450px");

        TextArea reasonField = new TextArea("LÃƒÂ½ do tÃ¡Â»Â« chÃ¡Â»â€˜i");
        reasonField.setWidthFull();
        reasonField.setRequired(true);
        reasonField.setMaxLength(1000);
        reasonField.setPlaceholder("NhÃ¡ÂºÂ­p lÃƒÂ½ do tÃ¡Â»Â« chÃ¡Â»â€˜i...");

        Button cancelButton = new Button("HÃ¡Â»Â§y", e -> dialog.close());
        Button confirmButton = new Button("XÃƒÂ¡c nhÃ¡ÂºÂ­n tÃ¡Â»Â« chÃ¡Â»â€˜i");
        confirmButton.addClickListener(e -> {
            String reason = reasonField.getValue();
            if (reason == null || reason.isBlank()) {
                Notification.show("Vui lÃƒÂ²ng nhÃ¡ÂºÂ­p lÃƒÂ½ do tÃ¡Â»Â« chÃ¡Â»â€˜i.", 4500, Position.TOP_END);
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
                Notification.show("Ã„ï¿½ÃƒÂ£ tÃ¡Â»Â« chÃ¡Â»â€˜i phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho", 3000, Position.TOP_END);
            } catch (WarehouseBusinessException ex) {
                confirmButton.setEnabled(true);
                Notification.show(ex.getMessage(), 4500, Position.TOP_END);
            }
        });

        dialog.add(reasonField);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }


    /*
     * ============================================================
     * POST TRANSFER
     * ============================================================
     */

    @Subscribe(
            id = "postButton",
            subject = "clickListener"
    )
    public void onPostButtonClick(
            final ClickEvent<JmixButton> event) {

        WarehouseTransaction current = getEditedEntity();
        if (current == null || current.getId() == null) {
            Notification.show("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho", 4500, Position.TOP_END);
            return;
        }
        if (current.getStatus() != WarehouseTransactionStatus.APPROVED) {
            Notification.show("ChÃ¡Â»â€° phiÃ¡ÂºÂ¿u APPROVED mÃ¡Â»â€ºi Ã„â€˜Ã†Â°Ã¡Â»Â£c POST", 4500, Position.TOP_END);
            return;
        }
        // PhiÃ¡ÂºÂ¿u APPROVED Ã„â€˜ÃƒÂ£ Ã„â€˜Ã†Â°Ã¡Â»Â£c lÃ†Â°u tÃ¡Â»Â« cÃƒÂ¡c bÃ†Â°Ã¡Â»â€ºc trÃ†Â°Ã¡Â»â€ºc, khÃƒÂ´ng gÃ¡Â»ï¿½i save()
        // vÃƒÂ¬ validation DRAFT khÃƒÂ´ng ÃƒÂ¡p dÃ¡Â»Â¥ng cho trÃ¡ÂºÂ¡ng thÃƒÂ¡i APPROVED.
        postSavedTransfer();
    }

    private void postSavedTransfer() {
        WarehouseTransaction current = getEditedEntity();

        if (current == null || current.getId() == null) {
            postButton.setEnabled(true);
            Notification.show(
                    "KhÃƒÂ´ng thÃ¡Â»Æ’ lÃ†Â°u phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho trÃ†Â°Ã¡Â»â€ºc khi POST",
                    5000,
                    Position.TOP_END
            );
            return;
        }

        try {

            WarehouseTransaction posted =
                    transferPostingService.postTransfer(
                            current.getId()
                    );

            /*
             * Ã„ï¿½Ã¡Â»â€œng bÃ¡Â»â„¢ entity trÃƒÂªn UI.
             */
            current.setStatus(
                    WarehouseTransactionStatus.POSTED
            );

            current.setPostedAt(
                    posted.getPostedAt()
            );

            current.setPostedBy(
                    posted.getPostedBy()
            );

            updateStatusLabel(current);
            refreshActions();

            Notification.show(
                    "POST transfer thÃƒÂ nh cÃƒÂ´ng",
                    3000,
                    Position.TOP_END
            );

            getUI().ifPresent(ui ->
                    ui.navigate(TransferListView.class)
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


    /*
     * ============================================================
     * REVERSE TRANSFER
     * ============================================================
     */

    @Subscribe(
            id = "reverseButton",
            subject = "clickListener"
    )
    public void onReverseButtonClick(
            final ClickEvent<JmixButton> event) {

        WarehouseTransaction current =
                getEditedEntity();

        /*
         * PhiÃ¡ÂºÂ¿u phÃ¡ÂºÂ£i tÃ¡Â»â€œn tÃ¡ÂºÂ¡i vÃƒÂ  Ã„â€˜ÃƒÂ£ Ã„â€˜Ã†Â°Ã¡Â»Â£c lÃ†Â°u.
         */
        if (current == null) {

            Notification.show(
                    "KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n",
                    5000,
                    Position.TOP_END
            );

            return;
        }

        if (current.getId() == null
                || entityStates.isNew(current)) {

            Notification.show(
                    "PhiÃ¡ÂºÂ¿u chÃ†Â°a Ã„â€˜Ã†Â°Ã¡Â»Â£c lÃ†Â°u",
                    5000,
                    Position.TOP_END
            );

            return;
        }

        /*
         * ChÃ¡Â»â€° POSTED mÃ¡Â»â€ºi Ã„â€˜Ã†Â°Ã¡Â»Â£c Reverse.
         */
        if (current.getStatus()
                != WarehouseTransactionStatus.POSTED) {

            Notification.show(
                    "ChÃ¡Â»â€° phiÃ¡ÂºÂ¿u POSTED mÃ¡Â»â€ºi Ã„â€˜Ã†Â°Ã¡Â»Â£c phÃƒÂ©p Reverse",
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

            /*
             * Ã„ï¿½Ã¡Â»â€¢i trÃ¡ÂºÂ¡ng thÃƒÂ¡i transaction gÃ¡Â»â€˜c
             * trÃƒÂªn UI.
             */
            current.setStatus(
                    WarehouseTransactionStatus.REVERSED
            );

            updateStatusLabel(current);
            refreshActions();

            Notification.show(
                    "Reverse transfer thÃƒÂ nh cÃƒÂ´ng. "
                            + "PhiÃ¡ÂºÂ¿u reversal: "
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


    /*
     * ============================================================
     * AFTER SAVE
     * ============================================================
     */

    @Subscribe
    public void onAfterSave(
            final AfterSaveEvent event) {
        refreshActions();

        if (!workflowActionInProgress) {
            Notification.show(
                    "Ã„ï¿½ÃƒÂ£ lÃ†Â°u phiÃ¡ÂºÂ¿u chuyÃ¡Â»Æ’n kho",
                    2500,
                    Position.TOP_END
            );
        }
    }


    /*
     * ============================================================
     * ACTIONS AND STATUS LABEL
     * ============================================================
     */

    private void refreshActions() {
        WarehouseTransaction transaction = getEditedEntity();
        if (transaction == null) {
            return;
        }

        boolean saved = !entityStates.isNew(transaction) && transaction.getId() != null;
        WarehouseTransactionStatus status = transaction.getStatus();
        boolean draft = status == WarehouseTransactionStatus.DRAFT;
        boolean pending = status == WarehouseTransactionStatus.PENDING_APPROVAL;
        boolean approved = status == WarehouseTransactionStatus.APPROVED;
        boolean posted = status == WarehouseTransactionStatus.POSTED;

        boolean editable = draft
                && authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT);
        boolean canSubmit = draft
                && authorizationService.isAllowed(WarehousePermissions.SUBMIT);
        boolean canApprove = pending
                && authorizationService.isAllowed(WarehousePermissions.APPROVE);
        boolean canReject = pending
                && authorizationService.isAllowed(WarehousePermissions.REJECT);
        boolean canPost = approved
                && authorizationService.isAllowed(WarehousePermissions.POST);
        boolean canReverse = saved
                && posted
                && transaction.getReversalOf() == null
                && authorizationService.isAllowed(WarehousePermissions.REVERSE);

        setReadOnly(!editable);
        documentNoField.setReadOnly(true);
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

        updateStatusLabel(transaction);
    }

    private void updateStatusLabel(
            WarehouseTransaction transaction) {
        WarehouseTransactionStatus status = transaction.getStatus();
        statusLabel.setText(status != null ? status.getId() : "-");
    }
}
