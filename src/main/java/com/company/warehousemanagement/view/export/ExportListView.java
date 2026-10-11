package com.company.warehousemanagement.view.export;

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

@Route(value = "export-issues", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.exportList")
@ViewDescriptor(path = "export-list-view.xml")
@LookupComponent("exportIssuesDataGrid")
@DialogMode(width = "64em")
public class ExportListView extends StandardListView<WarehouseTransaction> {

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
