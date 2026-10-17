package com.company.warehousemanagement.view.userwarehouse;

import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.textfield.TextField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "user-warehouses", layout = MainView.class)
@ViewController(id = "UserWarehouse.list")
@ViewDescriptor(path = "user-warehouse-list-view.xml")
@LookupComponent("userWarehousesDataGrid")
@DialogMode(width = "70em")
public class UserWarehouseListView extends StandardListView<UserWarehouse> {
    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<UserWarehouse> userWarehousesDl;

    private boolean searchListenerRegistered;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        applySearch();
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        userWarehousesDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        userWarehousesDl.load();
    }
}