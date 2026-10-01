package com.company.warehousemanagement.view.importreceipt;

import com.company.warehousemanagement.dto.AddImportReceiptItemCommand;
import com.company.warehousemanagement.dto.CreateImportReceiptCommand;
import com.company.warehousemanagement.dto.UpdateImportReceiptItemCommand;
import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.service.ImportReceiptService;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.JmixBigDecimalField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

@Route(value = "import-receipts/:id", layout = MainView.class)
@ViewController(id = "ImportReceipt.detail")
@ViewDescriptor(path = "import-receipt-detail-view.xml")
public class ImportReceiptDetailView extends StandardView
        implements BeforeEnterObserver, AfterNavigationObserver {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private ImportReceiptService importReceiptService;

    @Autowired
    private Notifications notifications;

    @ViewComponent
    private CollectionLoader<Warehouse> warehousesDl;

    @ViewComponent
    private CollectionContainer<Warehouse> warehousesDc;

    @ViewComponent
    private CollectionLoader<Partner> partnersDl;

    @ViewComponent
    private CollectionContainer<Partner> partnersDc;

    @ViewComponent
    private CollectionLoader<Product> productsDl;

    @ViewComponent
    private CollectionContainer<Product> productsDc;

    @ViewComponent
    private CollectionLoader<TransactionItem> itemsDl;

    @ViewComponent
    private TextField documentNoField;

    @ViewComponent
    private TypedDatePicker<LocalDate> documentDateField;

    @ViewComponent
    private EntityComboBox<Warehouse> warehouseField;

    @ViewComponent
    private EntityComboBox<Partner> partnerField;

    @ViewComponent
    private TextArea reasonField;

    @ViewComponent
    private TextField statusField;

    @ViewComponent
    private VerticalLayout itemsPanel;

    @ViewComponent
    private EntityComboBox<Product> productField;

    @ViewComponent
    private JmixBigDecimalField quantityField;

    @ViewComponent
    private TextField itemNoteField;

    @ViewComponent
    private DataGrid<TransactionItem> itemsDataGrid;

    @ViewComponent
    private JmixButton createReceiptButton;

    @ViewComponent
    private JmixButton addItemButton;

    @ViewComponent
    private JmixButton editItemButton;

    @ViewComponent
    private JmixButton updateItemButton;

    @ViewComponent
    private JmixButton removeItemButton;

    @ViewComponent
    private JmixButton confirmButton;

    @ViewComponent
    private JmixButton postButton;

    @ViewComponent
    private JmixButton cancelButton;

    private UUID receiptId;
    private UUID editingItemId;
    private WarehouseTransaction receipt;
    private boolean invalidRoute;
    private boolean viewInitialized;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String idValue = event.getRouteParameters()
                .get("id")
                .orElse("new");

        if ("new".equalsIgnoreCase(idValue)) {
            receiptId = null;
            return;
        }

        try {
            receiptId = UUID.fromString(idValue);
        } catch (IllegalArgumentException exception) {
            invalidRoute = true;
            event.forwardTo(ImportReceiptListView.class);
        }
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        initializeView();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        initializeView();
    }

    private void initializeView() {
        if (viewInitialized) {
            return;
        }

        if (invalidRoute) {
            return;
        }

        viewInitialized = true;

        warehousesDl.load();
        partnersDl.load();
        productsDl.load();

        // Bind the loaded containers explicitly. With a standalone StandardView,
        // relying only on the XML itemsContainer binding may leave the combo-box
        // data provider empty when the loaders are invoked during BeforeShowEvent.
        warehouseField.setItems(warehousesDc);
        partnerField.setItems(partnersDc);
        productField.setItems(productsDc);

        if (receiptId == null) {
            initializeNewReceipt();
        } else {
            reloadReceipt();
            reloadItems();
        }
    }

    @Subscribe("createReceiptButton")
    public void onCreateReceiptButtonClick(ClickEvent<JmixButton> event) {
        if (warehouseField.getValue() == null) {
            notifications.show("Hãy chọn kho nhận");
            return;
        }
        if (partnerField.getValue() == null) {
            notifications.show("Hãy chọn nhà cung cấp");
            return;
        }

        CreateImportReceiptCommand command = new CreateImportReceiptCommand();
        command.setWarehouseId(warehouseField.getValue().getId());
        command.setPartnerId(partnerField.getValue().getId());
        command.setDocumentDate(documentDateField.getValue());
        command.setReason(reasonField.getValue());

        try {
            WarehouseTransaction created = importReceiptService.createDraft(command);
            notifications.show("Đã tạo phiếu nhập " + created.getDocumentNo());
            getUI().ifPresent(ui ->
                    ui.navigate("import-receipts/" + created.getId())
            );
        } catch (WarehouseBusinessException exception) {
            showBusinessError(exception);
        }
    }

    @Subscribe("addItemButton")
    public void onAddItemButtonClick(ClickEvent<JmixButton> event) {
        if (!validateItemEditor()) {
            return;
        }

        AddImportReceiptItemCommand command = new AddImportReceiptItemCommand();
        command.setReceiptId(receiptId);
        command.setProductId(productField.getValue().getId());
        command.setQuantity(quantityField.getValue());
        command.setNote(itemNoteField.getValue());

        try {
            importReceiptService.addItem(command);
            notifications.show("Đã thêm sản phẩm vào phiếu");
            clearItemEditor();
            reloadItems();
        } catch (WarehouseBusinessException exception) {
            showBusinessError(exception);
        }
    }

    @Subscribe("editItemButton")
    public void onEditItemButtonClick(ClickEvent<JmixButton> event) {
        TransactionItem selected = itemsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            notifications.show("Hãy chọn một dòng hàng cần sửa");
            return;
        }

        editingItemId = selected.getId();
        productField.setValue(findProduct(selected.getProduct().getId()));
        quantityField.setValue(selected.getQuantity());
        itemNoteField.setValue(selected.getNote() == null ? "" : selected.getNote());
        productField.setReadOnly(true);
        updateActions();
    }

    @Subscribe("updateItemButton")
    public void onUpdateItemButtonClick(ClickEvent<JmixButton> event) {
        if (editingItemId == null || !validateItemEditor()) {
            return;
        }

        UpdateImportReceiptItemCommand command = new UpdateImportReceiptItemCommand();
        command.setItemId(editingItemId);
        command.setQuantity(quantityField.getValue());
        command.setNote(itemNoteField.getValue());

        try {
            importReceiptService.updateItem(command);
            notifications.show("Đã cập nhật dòng hàng");
            clearItemEditor();
            reloadItems();
        } catch (WarehouseBusinessException exception) {
            showBusinessError(exception);
        }
    }

    @Subscribe("removeItemButton")
    public void onRemoveItemButtonClick(ClickEvent<JmixButton> event) {
        TransactionItem selected = itemsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            notifications.show("Hãy chọn một dòng hàng cần xóa");
            return;
        }

        try {
            importReceiptService.removeItem(selected.getId());
            notifications.show("Đã xóa dòng hàng");
            clearItemEditor();
            reloadItems();
        } catch (WarehouseBusinessException exception) {
            showBusinessError(exception);
        }
    }

    @Subscribe("clearItemButton")
    public void onClearItemButtonClick(ClickEvent<JmixButton> event) {
        clearItemEditor();
    }

    @Subscribe("confirmButton")
    public void onConfirmButtonClick(ClickEvent<JmixButton> event) {
        executeReceiptAction(
                () -> importReceiptService.confirm(receiptId),
                "Đã xác nhận phiếu nhập"
        );
    }

    @Subscribe("postButton")
    public void onPostButtonClick(ClickEvent<JmixButton> event) {
        executeReceiptAction(
                () -> importReceiptService.post(receiptId),
                "Đã nhập kho và cập nhật tồn kho"
        );
    }

    @Subscribe("cancelButton")
    public void onCancelButtonClick(ClickEvent<JmixButton> event) {
        executeReceiptAction(
                () -> importReceiptService.cancel(receiptId),
                "Đã hủy phiếu nhập"
        );
    }

    @Subscribe("backButton")
    public void onBackButtonClick(ClickEvent<JmixButton> event) {
        getUI().ifPresent(ui -> ui.navigate(ImportReceiptListView.class));
    }

    private void initializeNewReceipt() {
        receipt = null;
        documentNoField.setValue("Tự động tạo");
        documentDateField.setValue(LocalDate.now());
        statusField.setValue(WarehouseTransactionStatus.DRAFT.getId());
        createReceiptButton.setVisible(true);
        itemsPanel.setVisible(false);
        setHeaderReadOnly(false);
        updateActions();
    }

    private void reloadReceipt() {
        receipt = dataManager.load(WarehouseTransaction.class)
                .id(receiptId)
                .fetchPlan(fetchPlan -> fetchPlan
                        .addFetchPlan(FetchPlan.BASE)
                        .add("destinationWarehouse", FetchPlan.BASE)
                        .add("partner", FetchPlan.BASE))
                .optional()
                .orElse(null);

        if (receipt == null) {
            notifications.show("Không tìm thấy phiếu nhập");
            getUI().ifPresent(ui -> ui.navigate(ImportReceiptListView.class));
            return;
        }

        documentNoField.setValue(receipt.getDocumentNo());
        documentDateField.setValue(receipt.getDocumentDate());
        warehouseField.setValue(findWarehouse(receipt.getDestinationWarehouse().getId()));
        partnerField.setValue(findPartner(receipt.getPartner().getId()));
        reasonField.setValue(receipt.getReason() == null ? "" : receipt.getReason());
        statusField.setValue(receipt.getStatus().getId());

        createReceiptButton.setVisible(false);
        itemsPanel.setVisible(true);
        setHeaderReadOnly(true);
        updateActions();
    }

    private void reloadItems() {
        if (receiptId == null) {
            return;
        }
        itemsDl.setQuery("select e from TransactionItem e "
                + "where e.transaction.id = :receiptId "
                + "order by e.lineNo");
        itemsDl.setParameter("receiptId", receiptId);
        itemsDl.load();
    }

    private boolean validateItemEditor() {
        if (productField.getValue() == null) {
            notifications.show("Hãy chọn sản phẩm");
            return false;
        }
        if (quantityField.getValue() == null) {
            notifications.show("Hãy nhập số lượng");
            return false;
        }
        return true;
    }

    private void clearItemEditor() {
        editingItemId = null;
        productField.clear();
        quantityField.clear();
        itemNoteField.clear();
        updateActions();
    }

    private void updateActions() {
        boolean existing = receipt != null;
        boolean draft = existing
                && receipt.getStatus() == WarehouseTransactionStatus.DRAFT;
        boolean confirmed = existing
                && receipt.getStatus() == WarehouseTransactionStatus.CONFIRMED;

        productField.setReadOnly(!draft || editingItemId != null);
        quantityField.setReadOnly(!draft);
        itemNoteField.setReadOnly(!draft);

        addItemButton.setEnabled(draft && editingItemId == null);
        editItemButton.setEnabled(draft);
        updateItemButton.setEnabled(draft && editingItemId != null);
        removeItemButton.setEnabled(draft);
        confirmButton.setEnabled(draft);
        postButton.setEnabled(confirmed);
        cancelButton.setEnabled(draft || confirmed);
    }

    private void setHeaderReadOnly(boolean readOnly) {
        documentDateField.setReadOnly(readOnly);
        warehouseField.setReadOnly(readOnly);
        partnerField.setReadOnly(readOnly);
        reasonField.setReadOnly(readOnly);
    }

    private void executeReceiptAction(ReceiptAction action, String successMessage) {
        try {
            action.execute();
            notifications.show(successMessage);
            clearItemEditor();
            reloadReceipt();
            reloadItems();
        } catch (WarehouseBusinessException exception) {
            showBusinessError(exception);
        }
    }

    private Warehouse findWarehouse(UUID id) {
        return warehousesDc.getItems().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private Partner findPartner(UUID id) {
        return partnersDc.getItems().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private Product findProduct(UUID id) {
        return productsDc.getItems().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void showBusinessError(WarehouseBusinessException exception) {
        notifications.show(exception.getMessage());
    }

    @FunctionalInterface
    private interface ReceiptAction {
        WarehouseTransaction execute();
    }
}
