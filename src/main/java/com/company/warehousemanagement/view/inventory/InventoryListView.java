package com.company.warehousemanagement.view.inventory;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.textfield.TextField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.View.BeforeShowEvent;
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
public class InventoryListView extends StandardListView<Inventory> {
    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<Inventory> inventoryDl;

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
        inventoryDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        inventoryDl.load();
    }
}
