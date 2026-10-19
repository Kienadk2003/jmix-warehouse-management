package com.company.warehousemanagement.view.stocktake;

import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeItem;
import com.company.warehousemanagement.entity.StocktakeStatus;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.service.StocktakeService;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.AccessManager;
import io.jmix.core.EntityStates;
import io.jmix.core.accesscontext.SpecificOperationAccessContext;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionPropertyContainer;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.StandardDetailView.AfterSaveEvent;
import io.jmix.flowui.view.StandardDetailView.InitEntityEvent;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

@Route(value = "stocktakes/:id", layout = MainView.class)
@ViewController(id = "Stocktake.detail")
@ViewDescriptor(path = "stocktake-detail-view.xml")
@EditedEntityContainer("stocktakeDc")
public class StocktakeDetailView extends StandardDetailView<Stocktake> {

    @Autowired
    private Messages messages;

    @Autowired
    private StocktakeService stocktakeService;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private AccessManager accessManager;

    @ViewComponent
    private DataContext dataContext;

    @ViewComponent
    private CollectionPropertyContainer<StocktakeItem> itemsDc;

    @ViewComponent
    private DataGrid<StocktakeItem> itemsDataGrid;

    @ViewComponent
    private EntityComboBox<Warehouse> warehouseField;

    @ViewComponent
    private JmixButton snapshotButton;

    @ViewComponent
    private JmixButton submitButton;

    @ViewComponent
    private JmixButton approveButton;

    @ViewComponent
    private JmixButton rejectButton;

    @ViewComponent
    private JmixButton saveButton;

    @ViewComponent
    private TextField statusField;

    @ViewComponent
    private TextField snapshotAtField;

    @Subscribe
    public void onInitEntity(InitEntityEvent<Stocktake> event) {
        stocktakeService.prepareDraft(event.getEntity());
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        refreshActions();
    }

    @Subscribe
    public void onAfterSave(AfterSaveEvent event) {
        refreshActions();
        Notification.show("Đã lưu phiếu kiểm kê", 2500, Position.TOP_END);
    }

    @Subscribe("snapshotButton")
    public void onSnapshotButtonClick(ClickEvent<JmixButton> event) {
        Stocktake stocktake = getEditedEntity();
        if (stocktake == null) {
            showError("Không tìm thấy phiếu kiểm kê.");
            return;
        }

        if (!hasPermission(WarehousePermissions.COUNT_STOCK)) {
            showError("Bạn không có quyền tạo snapshot kiểm kê.");
            return;
        }
        if (entityStates.isNew(stocktake)) {
            showError("Hãy lưu phiếu kiểm kê trước khi chụp tồn.");
            return;
        }
        if (stocktake.getStatus() != StocktakeStatus.DRAFT) {
            showError("Chỉ phiếu DRAFT mới được tạo snapshot.");
            return;
        }
        if (warehouseField.getValue() == null) {
            showError("Hãy chọn kho cần kiểm kê.");
            return;
        }
        if (!itemsDc.getItems().isEmpty()) {
            showError("Phiếu đã có snapshot; không thể tạo lại snapshot.");
            return;
        }

        try {
            var snapshotLines = stocktakeService.loadSnapshot(warehouseField.getValue().getId());
            for (var line : snapshotLines) {
                StocktakeItem item = dataContext.create(StocktakeItem.class);
                item.setStocktake(stocktake);
                item.setProduct(dataContext.merge(line.product()));
                item.setBookQuantity(line.bookQuantity());
                itemsDc.getMutableItems().add(item);
            }

            stocktake.setSnapshotAt(OffsetDateTime.now());
            stocktake.setStatus(StocktakeStatus.COUNTING);
            updateStatus(stocktake);
            refreshActions();

            Notification.show(
                    "Đã tạo snapshot. Nhập số lượng thực tế cho các sản phẩm, rồi lưu phiếu.",
                    4000,
                    Position.TOP_END
            );
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        }
    }

    @Subscribe("submitButton")
    public void onSubmitButtonClick(ClickEvent<JmixButton> event) {
        Stocktake stocktake = getEditedEntity();
        if (stocktake == null || entityStates.isNew(stocktake)) {
            showError("Hãy lưu phiếu kiểm kê trước khi gửi duyệt.");
            return;
        }
        if (stocktake.getStatus() != StocktakeStatus.COUNTING) {
            showError("Chỉ phiếu đang kiểm kê mới được gửi duyệt.");
            return;
        }

        submitButton.setEnabled(false);
        try {
            // Persist all counted quantities before the service validates/submits.
            dataContext.save();
            Stocktake submitted = stocktakeService.submit(stocktake.getId());
            // Merge the authoritative persisted state instead of manually mutating
            // the tracked entity, which could leave the detail view dirty.
            dataContext.merge(submitted);
            refreshActions();
            Notification.show("Đã gửi phiếu kiểm kê để chờ duyệt.", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        } finally {
            refreshActions();
        }
    }

    @Subscribe("approveButton")
    public void onApproveButtonClick(ClickEvent<JmixButton> event) {
        Stocktake stocktake = getEditedEntity();
        if (stocktake == null || entityStates.isNew(stocktake)) {
            showError("Hãy lưu phiếu kiểm kê trước khi duyệt.");
            return;
        }
        if (stocktake.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            showError("Chỉ phiếu đang chờ duyệt mới được duyệt.");
            return;
        }

        approveButton.setEnabled(false);
        try {
            Stocktake approved = stocktakeService.approve(stocktake.getId());
            dataContext.merge(approved);
            refreshActions();
            Notification.show(
                    "Đã duyệt kiểm kê và ghi nhận chênh lệch tồn kho.",
                    3000,
                    Position.TOP_END
            );
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        } finally {
            refreshActions();
        }
    }

    @Subscribe("rejectButton")
    public void onRejectButtonClick(ClickEvent<JmixButton> event) {
        Stocktake stocktake = getEditedEntity();
        if (stocktake == null || entityStates.isNew(stocktake)) {
            showError("Hãy lưu phiếu kiểm kê trước khi từ chối.");
            return;
        }
        if (stocktake.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            showError("Chỉ phiếu đang chờ duyệt mới được từ chối.");
            return;
        }

        rejectButton.setEnabled(false);
        try {
            Stocktake rejected = stocktakeService.reject(stocktake.getId());
            dataContext.merge(rejected);
            refreshActions();
            Notification.show("Đã từ chối phiếu kiểm kê.", 3000, Position.TOP_END);
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        } finally {
            refreshActions();
        }
    }

    private void refreshActions() {
        Stocktake stocktake = getEditedEntity();
        if (stocktake == null) {
            return;
        }

        boolean saved = !entityStates.isNew(stocktake);
        boolean draft = stocktake.getStatus() == StocktakeStatus.DRAFT;
        boolean counting = stocktake.getStatus() == StocktakeStatus.COUNTING;
        boolean pending = stocktake.getStatus() == StocktakeStatus.PENDING_APPROVAL;
        boolean hasItems = !itemsDc.getItems().isEmpty();

        boolean canCount = hasPermission(WarehousePermissions.COUNT_STOCK);
        boolean canApprove = hasPermission(WarehousePermissions.APPROVE_STOCKTAKE);
        boolean canReject = hasPermission(WarehousePermissions.REJECT_STOCKTAKE);

        warehouseField.setReadOnly(!draft || hasItems || !canCount);
        itemsDataGrid.setEnabled(counting && canCount);

        snapshotButton.setVisible(saved && draft && !hasItems && canCount);
        submitButton.setVisible(saved && counting && hasItems && canCount);
        approveButton.setVisible(saved && pending && canApprove);
        rejectButton.setVisible(saved && pending && canReject);
        saveButton.setVisible((draft || counting) && canCount);

        updateStatus(stocktake);
    }

    private boolean hasPermission(String permission) {
        SpecificOperationAccessContext context = new SpecificOperationAccessContext(permission);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    private void updateStatus(Stocktake stocktake) {
        statusField.setValue(stocktake.getStatus() == null ? "" : messages.getMessage("com.company.warehousemanagement.entity/StocktakeStatus." + stocktake.getStatus().name()));
        snapshotAtField.setValue(stocktake.getSnapshotAt() == null
                ? ""
                : stocktake.getSnapshotAt().toString());
    }

    private void showError(String message) {
        Notification.show(message, 4500, Position.TOP_END);
    }
}
