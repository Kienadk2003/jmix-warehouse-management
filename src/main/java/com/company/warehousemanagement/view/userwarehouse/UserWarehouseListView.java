package com.company.warehousemanagement.view.userwarehouse;

import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "user-warehouses", layout = MainView.class)
@ViewController(id = "UserWarehouse.list")
@ViewDescriptor(path = "user-warehouse-list-view.xml")
@LookupComponent("userWarehousesDataGrid")
@DialogMode(width = "70em")
public class UserWarehouseListView extends StandardListView<UserWarehouse> {
}