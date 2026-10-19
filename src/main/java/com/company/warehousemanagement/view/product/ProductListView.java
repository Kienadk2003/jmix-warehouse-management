package com.company.warehousemanagement.view.product;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "products", layout = MainView.class)
@ViewController(id = "Product.list")
@ViewDescriptor(path = "product-list-view.xml")
@LookupComponent("productsDataGrid")
@DialogMode(width = "64em")
public class ProductListView extends StandardListView<Product> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<Product> productsDl;

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
        productsDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        productsDl.load();
    }
}
