package com.company.warehousemanagement.view.importreceipt;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "import-receipts", layout = MainView.class)
@ViewController(id = "ImportReceipt.list")
@ViewDescriptor(path = "import-receipt-list-view.xml")
@LookupComponent("receiptsDataGrid")
@DialogMode(width = "80em")
public class ImportReceiptListView extends StandardListView<WarehouseTransaction> {

    @ViewComponent
    private DataGrid<WarehouseTransaction> receiptsDataGrid;

    @ViewComponent
    private CollectionLoader<WarehouseTransaction> receiptsDl;

    @Autowired
    private Notifications notifications;

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @ViewComponent
    private JmixButton createButton;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        createButton.setVisible(
                authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT)
        );
    }

    @Subscribe("createButton")
    public void onCreateButtonClick(ClickEvent<JmixButton> event) {
        getUI().ifPresent(ui -> ui.navigate("import-receipts/new"));
    }

    @Subscribe("openButton")
    public void onOpenButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction selected = receiptsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            notifications.show("Hãy chọn một phiếu nhập");
            return;
        }

        getUI().ifPresent(ui ->
                ui.navigate("import-receipts/" + selected.getId())
        );
    }

    @Subscribe("refreshButton")
    public void onRefreshButtonClick(ClickEvent<JmixButton> event) {
        receiptsDl.load();
    }
}
