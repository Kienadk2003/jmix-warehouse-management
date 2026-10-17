package com.company.warehousemanagement.view.stocktake;

import com.company.warehousemanagement.entity.Stocktake;
import com.vaadin.flow.component.textfield.TextField;
import io.jmix.flowui.model.CollectionLoader;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import org.springframework.beans.factory.annotation.Autowired;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "stocktakes", layout = MainView.class)
@ViewController(id = "Stocktake.list")
@ViewDescriptor(path = "stocktake-list-view.xml")
@LookupComponent("stocktakesDataGrid")
public class StocktakeListView extends StandardListView<Stocktake> {

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @ViewComponent
    private JmixButton createButton;

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<Stocktake> stocktakesDl;

    private boolean searchListenerRegistered;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        createButton.setVisible(
                authorizationService.isAllowed(WarehousePermissions.COUNT_STOCK)
        );
        applySearch();
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        stocktakesDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        stocktakesDl.load();
    }
}
