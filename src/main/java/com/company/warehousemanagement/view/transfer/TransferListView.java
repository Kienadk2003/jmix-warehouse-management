package com.company.warehousemanagement.view.transfer;

import com.company.warehousemanagement.entity.WarehouseTransaction;
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
}