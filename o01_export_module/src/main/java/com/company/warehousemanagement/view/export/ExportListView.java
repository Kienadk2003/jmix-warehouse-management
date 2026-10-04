package com.company.warehousemanagement.view.export;

import com.company.warehousemanagement.entity.WarehouseTransaction;
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
}
