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
        Notification.show(
                "Ä�Ã£ lÆ°u phiáº¿u kiá»ƒm kÃª",
                2500,
                Position.TOP_END
        );
    }

    @Subscribe("snapshotButton")
    public void onSnapshotButtonClick(ClickEvent<JmixButton> event) {
        Stocktake stocktake = getEditedEntity();

        if (!hasPermission(WarehousePermissions.COUNT_STOCK)) {
            showError("Bạn không có quyền tạo snapshot kiểm kê");
            return;
        }

        if (entityStates.isNew(stocktake)) {
            showError("HÃ£y lÆ°u phiáº¿u kiá»ƒm kÃª trÆ°á»›c khi chá»¥p tá»“n Ä‘áº§u ká»³");
            return;
        }

        if (stocktake.getStatus() != StocktakeStatus.DRAFT) {
            showError("Chá»‰ phiáº¿u DRAFT má»›i Ä‘Æ°á»£c táº¡o snapshot");
            return;
        }

        if (warehouseField.getValue() == null) {
            showError("HÃ£y chá»�n kho kiá»ƒm kÃª");
            return;
        }

        if (!itemsDc.getItems().isEmpty()) {
            showError("Phiáº¿u Ä‘Ã£ cÃ³ snapshot; khÃ´ng thá»ƒ táº¡o láº¡i snapshot");
            return;
        }

        try {
            var snapshotLines = stocktakeService.loadSnapshot(
                    warehouseField.getValue().getId()
            );

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
                    "Ä�Ã£ táº¡o snapshot. Nháº­p sá»‘ Ä‘áº¿m thá»±c táº¿ rá»“i lÆ°u phiáº¿u.",
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

        if (entityStates.isNew(stocktake)) {
            showError("HÃ£y lÆ°u phiáº¿u kiá»ƒm kÃª trÆ°á»›c khi gá»­i duyá»‡t");
            return;
        }

        if (stocktake.getStatus() != StocktakeStatus.COUNTING) {
            showError("Chá»‰ phiáº¿u Ä‘ang kiá»ƒm kÃª má»›i Ä‘Æ°á»£c gá»­i duyá»‡t");
            return;
        }

        try {
            // Save the count quantities before submitting the persisted entity.
            dataContext.save();

            Stocktake submitted = stocktakeService.submit(stocktake.getId());
            dataContext.merge(submitted);
            // Keep the root entity in this view's DataContext in sync with
            // the status changed by the service, so buttons refresh correctly.
            stocktake.setStatus(StocktakeStatus.PENDING_APPROVAL);

            refreshActions();
            Notification.show(
                    "Ä�Ã£ gá»­i phiáº¿u kiá»ƒm kÃª Ä‘á»ƒ chá»� duyá»‡t",
                    3000,
                    Position.TOP_END
            );
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        }
    }

    @Subscribe("approveButton")
    public void onApproveButtonClick(ClickEvent<JmixButton> event) {
        Stocktake stocktake = getEditedEntity();

        if (entityStates.isNew(stocktake)) {
            showError("HÃ£y lÆ°u phiáº¿u kiá»ƒm kÃª trÆ°á»›c khi duyá»‡t");
            return;
        }

        if (stocktake.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            showError("Chá»‰ phiáº¿u Ä‘ang chá»� duyá»‡t má»›i Ä‘Æ°á»£c duyá»‡t");
            return;
        }

        try {
            Stocktake approved = stocktakeService.approve(stocktake.getId());
            dataContext.merge(approved);
            stocktake.setStatus(StocktakeStatus.APPROVED);
            stocktake.setApprovedAt(approved.getApprovedAt());

            refreshActions();
            Notification.show(
                    "Ä�Ã£ duyá»‡t kiá»ƒm kÃª vÃ  cáº­p nháº­t tá»“n kho",
                    3000,
                    Position.TOP_END
            );
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
        }
    }

    @Subscribe("rejectButton")
    public void onRejectButtonClick(ClickEvent<JmixButton> event) {
        Stocktake stocktake = getEditedEntity();

        if (entityStates.isNew(stocktake)) {
            showError("HÃ£y lÆ°u phiáº¿u kiá»ƒm kÃª trÆ°á»›c khi tá»« chá»‘i");
            return;
        }

        if (stocktake.getStatus() != StocktakeStatus.PENDING_APPROVAL) {
            showError("Chá»‰ phiáº¿u Ä‘ang chá»� duyá»‡t má»›i Ä‘Æ°á»£c tá»« chá»‘i");
            return;
        }

        try {
            Stocktake rejected = stocktakeService.reject(stocktake.getId());
            dataContext.merge(rejected);
            stocktake.setStatus(StocktakeStatus.REJECTED);

            refreshActions();
            Notification.show(
                    "Ä�Ã£ tá»« chá»‘i phiáº¿u kiá»ƒm kÃª",
                    3000,
                    Position.TOP_END
            );
        } catch (WarehouseBusinessException exception) {
            showError(exception.getMessage());
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
        boolean editable = draft || counting;

        warehouseField.setReadOnly(!draft || hasItems || !canCount);
        itemsDataGrid.setEnabled(counting && canCount);

        snapshotButton.setVisible(
                saved && draft && !hasItems && canCount
        );
        submitButton.setVisible(
                saved && counting && hasItems && canCount
        );
        approveButton.setVisible(
                saved && pending && canApprove
        );
        rejectButton.setVisible(
                saved && pending && canReject
        );
        saveButton.setVisible(editable && canCount);

        updateStatus(stocktake);
    }

    private boolean hasPermission(String permission) {
        SpecificOperationAccessContext context =
                new SpecificOperationAccessContext(permission);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    private void updateStatus(Stocktake stocktake) {
        statusField.setValue(stocktake.getStatus() == null
                ? ""
                : stocktake.getStatus().getId());
        snapshotAtField.setValue(stocktake.getSnapshotAt() == null
                ? ""
                : stocktake.getSnapshotAt().toString());
    }

    private void showError(String message) {
        Notification.show(message, 4500, Position.TOP_END);
    }
}
