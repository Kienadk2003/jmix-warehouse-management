package com.company.warehousemanagement.view.transfer;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.service.ReversalService;
import com.company.warehousemanagement.service.TransferIssueService;
import com.company.warehousemanagement.service.TransferPostingService;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
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
    private ReversalService reversalService;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private WarehouseAuthorizationService authorizationService;

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

        WarehouseTransaction transaction =
                getEditedEntity();

        if (transaction == null) {
            return;
        }

        updateStatusLabel(transaction);

        boolean editable =
                transaction.getStatus()
                        == WarehouseTransactionStatus.DRAFT
                        && authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT);

        boolean saved =
                !entityStates.isNew(transaction);

        boolean canPost =
                saved
                        && transaction.getStatus() == WarehouseTransactionStatus.DRAFT
                        && authorizationService.isAllowed(WarehousePermissions.POST);

        boolean canReverse = saved
                && transaction.getStatus() == WarehouseTransactionStatus.POSTED
                && transaction.getReversalOf() == null
                && authorizationService.isAllowed(WarehousePermissions.REVERSE);

        /*
         * DRAFT:
         * - được sửa
         * - được POST
         *
         * POSTED:
         * - không được sửa
         * - được REVERSE
         *
         * REVERSED:
         * - không được sửa
         * - không được POST
         * - không được REVERSE
         */

        setReadOnly(!editable);

        documentNoField.setReadOnly(true);

        addItemButton.setVisible(editable);
        editItemButton.setVisible(editable);
        removeItemButton.setVisible(editable);

        postButton.setVisible(canPost);
        reverseButton.setVisible(canReverse);
    }


    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */

    @Subscribe
    public void onValidation(
            final ValidationEvent event) {

        try {

            transferIssueService.validateDraft(
                    getEditedEntity()
            );

        } catch (WarehouseBusinessException e) {

            event.getErrors().add(
                    e.getMessage()
            );
        }
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

        WarehouseTransaction current =
                getEditedEntity();

        /*
         * Phiếu phải được lưu trước.
         */
        if (current == null
                || current.getId() == null
                || entityStates.isNew(current)) {

            Notification.show(
                    "Bạn phải lưu phiếu DRAFT trước khi POST",
                    5000,
                    Position.TOP_END
            );

            return;
        }

        /*
         * Chỉ DRAFT mới được POST.
         */
        if (current.getStatus()
                != WarehouseTransactionStatus.DRAFT) {

            Notification.show(
                    "Chỉ phiếu DRAFT mới được phép POST",
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
             * Đồng bộ entity trên UI.
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

            /*
             * Sau khi POST:
             * - khóa chỉnh sửa
             * - ẩn POST
             * - hiện REVERSE
             */
            setReadOnly(true);

            addItemButton.setVisible(false);
            editItemButton.setVisible(false);
            removeItemButton.setVisible(false);

            postButton.setVisible(false);
            reverseButton.setVisible(true);

            Notification.show(
                    "POST transfer thành công",
                    3000,
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
         * Phiếu phải tồn tại và đã được lưu.
         */
        if (current == null) {

            Notification.show(
                    "Không tìm thấy phiếu chuyển",
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

        /*
         * Chỉ POSTED mới được Reverse.
         */
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

            /*
             * Đổi trạng thái transaction gốc
             * trên UI.
             */
            current.setStatus(
                    WarehouseTransactionStatus.REVERSED
            );

            updateStatusLabel(current);

            /*
             * Phiếu REVERSED không được chỉnh sửa nữa.
             */
            setReadOnly(true);

            addItemButton.setVisible(false);
            editItemButton.setVisible(false);
            removeItemButton.setVisible(false);

            postButton.setVisible(false);
            reverseButton.setVisible(false);

            Notification.show(
                    "Reverse transfer thành công. "
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


    /*
     * ============================================================
     * AFTER SAVE
     * ============================================================
     */

    @Subscribe
    public void onAfterSave(
            final AfterSaveEvent event) {

        WarehouseTransaction transaction =
                getEditedEntity();

        if (transaction == null) {
            return;
        }

        boolean saved =
                !entityStates.isNew(transaction);

        boolean canPost =
                saved
                        && transaction.getStatus()
                        == WarehouseTransactionStatus.DRAFT
                        && authorizationService.isAllowed(WarehousePermissions.POST);

        boolean canReverse = saved
                && transaction.getStatus() == WarehouseTransactionStatus.POSTED
                && transaction.getReversalOf() == null
                && authorizationService.isAllowed(WarehousePermissions.REVERSE);

        postButton.setVisible(canPost);
        reverseButton.setVisible(canReverse);

        updateStatusLabel(transaction);

        Notification.show(
                "Transfer draft saved successfully",
                2500,
                Position.TOP_END
        );
    }


    /*
     * ============================================================
     * STATUS LABEL
     * ============================================================
     */

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
