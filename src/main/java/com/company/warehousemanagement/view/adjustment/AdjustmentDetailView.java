package com.company.warehousemanagement.view.adjustment;

import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.service.AdjustmentIssueService;
import com.company.warehousemanagement.service.AdjustmentPostingService;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
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

@Route(
        value = "adjustments/:id",
        layout = MainView.class
)
@ViewController(
        id = "WarehouseTransaction.adjustmentDetail"
)
@ViewDescriptor(
        path = "adjustment-detail-view.xml"
)
@EditedEntityContainer("warehouseTransactionDc")
public class AdjustmentDetailView
        extends StandardDetailView<WarehouseTransaction> {

    @Autowired
    private AdjustmentIssueService adjustmentIssueService;

    @Autowired
    private AdjustmentPostingService adjustmentPostingService;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @Autowired
    private DataManager dataManager;

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

    @Subscribe
    public void onInitEntity(
            InitEntityEvent<WarehouseTransaction> event) {

        adjustmentIssueService.prepareNewDraft(
                event.getEntity()
        );
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {

        WarehouseTransaction transaction =
                getEditedEntity();

        if (transaction == null) {
            return;
        }

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

        setReadOnly(!editable);

        postButton.setVisible(canPost);

        addItemButton.setVisible(editable);
        editItemButton.setVisible(editable);
        removeItemButton.setVisible(editable);

        updateStatusField(transaction);
    }

    @Subscribe
    public void onValidation(ValidationEvent event) {

        try {
            adjustmentIssueService.validateDraft(
                    getEditedEntity()
            );
        } catch (WarehouseBusinessException e) {
            event.getErrors().add(
                    e.getMessage()
            );
        }
    }

    @Subscribe
    public void onAfterSave(AfterSaveEvent event) {

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

        postButton.setVisible(canPost);

        updateStatusField(transaction);

        Notification.show(
                "Adjustment draft saved successfully",
                2500,
                Position.TOP_END
        );
    }

    @Subscribe(
            id = "postButton",
            subject = "clickListener"
    )
    public void onPostButtonClick(
            ClickEvent<JmixButton> event) {

        WarehouseTransaction transaction =
                getEditedEntity();

        if (transaction == null
                || transaction.getId() == null
                || entityStates.isNew(transaction)) {

            Notification.show(
                    "Bạn phải lưu phiếu DRAFT trước khi POST",
                    5000,
                    Position.TOP_END
            );

            return;
        }

        if (transaction.getStatus()
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
                    adjustmentPostingService.postAdjustment(
                            transaction.getId()
                    );

            transaction.setStatus(
                    WarehouseTransactionStatus.POSTED
            );

            transaction.setPostedAt(
                    posted.getPostedAt()
            );

            transaction.setPostedBy(
                    posted.getPostedBy()
            );

            updateStatusField(transaction);

            setReadOnly(true);

            addItemButton.setVisible(false);
            editItemButton.setVisible(false);
            removeItemButton.setVisible(false);
            postButton.setVisible(false);

            Notification.show(
                    "POST Adjustment thành công",
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

    @Subscribe(
            id = "addItemButton",
            subject = "clickListener"
    )
    public void onAddItemButtonClick(
            ClickEvent<JmixButton> event) {

        WarehouseTransaction transaction =
                getEditedEntity();

        if (transaction == null) {
            return;
        }

        TransactionItem item =
                dataManager.create(
                        TransactionItem.class
                );

        item.setTransaction(transaction);

        item.setLineNo(
                itemsDc.getItems().size() + 1
        );

        itemsDc.getMutableItems().add(item);

        DataGridEditor<TransactionItem> editor =
                itemsDataGrid.getEditor();

        editor.editItem(item);
    }

    @Subscribe(
            id = "editItemButton",
            subject = "clickListener"
    )
    public void onEditItemButtonClick(
            ClickEvent<JmixButton> event) {

        TransactionItem selectedItem =
                itemsDataGrid.getSingleSelectedItem();

        if (selectedItem == null) {
            Notification.show(
                    "Vui lòng chọn một dòng item",
                    3000,
                    Position.TOP_END
            );
            return;
        }

        if (itemsDataGrid.getEditor().isOpen()) {
            return;
        }

        itemsDataGrid
                .getEditor()
                .editItem(selectedItem);
    }

    @Subscribe(
            id = "removeItemButton",
            subject = "clickListener"
    )
    public void onRemoveItemButtonClick(
            ClickEvent<JmixButton> event) {

        TransactionItem selectedItem =
                itemsDataGrid.getSingleSelectedItem();

        if (selectedItem == null) {
            Notification.show(
                    "Vui lòng chọn một dòng item",
                    3000,
                    Position.TOP_END
            );
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

        for (TransactionItem item :
                itemsDc.getMutableItems()) {

            item.setLineNo(lineNo++);
        }
    }

    private void updateStatusField(
            WarehouseTransaction transaction) {

        if (transaction.getStatus() == null) {
            statusField.setTypedValue("-");
            return;
        }

        statusField.setTypedValue(
                transaction
                        .getStatus()
                        .getId()
        );
    }
}
