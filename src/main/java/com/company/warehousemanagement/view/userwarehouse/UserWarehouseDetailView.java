package com.company.warehousemanagement.view.userwarehouse;

import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "user-warehouses/:id", layout = MainView.class)
@ViewController(id = "UserWarehouse.detail")
@ViewDescriptor(path = "user-warehouse-detail-view.xml")
@EditedEntityContainer("userWarehouseDc")
public class UserWarehouseDetailView extends StandardDetailView<UserWarehouse> {
}