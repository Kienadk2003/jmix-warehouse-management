package com.company.warehousemanagement.view.warehouse;

import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "warehouses", layout = MainView.class)
@ViewController(id = "Warehouse.list")
@ViewDescriptor(path = "warehouse-list-view.xml")
@LookupComponent("warehousesDataGrid")
@DialogMode(width = "64em")
public class WarehouseListView extends StandardListView<Warehouse> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<Warehouse> warehousesDl;

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
        warehousesDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        warehousesDl.load();
    }
}
