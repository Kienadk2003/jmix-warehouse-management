package com.company.warehousemanagement.view.transfer;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import org.springframework.beans.factory.annotation.Autowired;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "transfers", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.transferList")
@ViewDescriptor(path = "transfer-list-view.xml")
@LookupComponent("transfersDataGrid")
@DialogMode(width = "80em")
public class TransferListView
        extends StandardListView<WarehouseTransaction> {

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
}