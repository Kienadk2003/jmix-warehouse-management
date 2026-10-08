package com.company.warehousemanagement.view.employeeassignments;

import com.company.warehousemanagement.entity.UserProductCategory;
import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

import java.util.List;
import java.util.stream.Collectors;

@Route(value = "user_warehouses", layout = MainView.class)
@ViewController(id = "employeeAssignment-list")
@ViewDescriptor(path = "employee-assignment-list-view.xml")
@LookupComponent("userWarehousesDataGrid")
@DialogMode(width = "64em")
public class EmployeeAssignmentListView extends StandardListView<UserWarehouse> {

    @ViewComponent
    private CollectionLoader<UserWarehouse> userWarehousesDl;

    private final DataManager dataManager;

    public EmployeeAssignmentListView(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Subscribe(id = "userWarehousesDl", target = Target.DATA_LOADER)
    public void onUserWarehousesDlPostLoad(
            CollectionLoader.PostLoadEvent<UserWarehouse> event) {

        for (UserWarehouse userWarehouse : event.getLoadedEntities()) {

            if (userWarehouse.getUser() == null) {
                userWarehouse.setProductCategories("");
                continue;
            }

            List<UserProductCategory> assignments =
                    dataManager.load(UserProductCategory.class)
                            .query("""
                        select e
                        from UserProductCategory e
                        where e.user = :user
                        """)
                            .parameter("user", userWarehouse.getUser())
                            .fetchPlan("_base")
                            .list();

            String categories = assignments.stream()
                    .map(UserProductCategory::getProductCategory)
                    .filter(category -> category != null)
                    .map(category -> category.getName())
                    .collect(Collectors.joining(", "));

            userWarehouse.setProductCategories(categories);
        }
    }
}