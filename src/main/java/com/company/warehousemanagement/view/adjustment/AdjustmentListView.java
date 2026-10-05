package com.company.warehousemanagement.view.adjustment;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(
        value = "adjustments",
        layout = MainView.class
)
@ViewController(
        id = "WarehouseTransaction.adjustmentList"
)
@ViewDescriptor(
        path = "adjustment-list-view.xml"
)
@LookupComponent("adjustmentsDataGrid")
@DialogMode(
        width = "80em",
        height = "50em"
)
public class AdjustmentListView
        extends StandardListView<WarehouseTransaction> {
}