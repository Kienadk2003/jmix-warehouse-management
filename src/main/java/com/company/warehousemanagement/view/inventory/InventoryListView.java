package com.company.warehousemanagement.view.inventory;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(
        value = "inventory",
        layout = MainView.class
)
@ViewController(
        id = "Inventory.list"
)
@ViewDescriptor(
        path = "inventory-list-view.xml"
)
@LookupComponent("inventoryDataGrid")
@DialogMode(
        width = "80em",
        height = "50em"
)
public class InventoryListView
        extends StandardListView<Inventory> {
}