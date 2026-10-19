package com.company.warehousemanagement.view.operations;

import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.textfield.TextField;
import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "warehouse-operations/stocktake-adjustment", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.stocktakeAdjustmentList")
@ViewDescriptor(path = "stocktake-adjustment-list-view.xml")
public class StocktakeAdjustmentListView extends StandardView {

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @ViewComponent
    private JmixButton createStocktakeButton;

    @ViewComponent
    private JmixButton createAdjustmentButton;

    @ViewComponent
    private TextField stocktakeSearchField;

    @ViewComponent
    private TextField adjustmentSearchField;

    @ViewComponent
    private CollectionLoader<Stocktake> stocktakesDl;

    @ViewComponent
    private CollectionLoader<WarehouseTransaction> adjustmentsDl;

    private boolean searchListenersRegistered;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        createStocktakeButton.setVisible(
                authorizationService.isAllowed(WarehousePermissions.COUNT_STOCK));
        createAdjustmentButton.setVisible(
                authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT));
        if (!searchListenersRegistered) {
            stocktakeSearchField.addValueChangeListener(valueChangeEvent -> applyStocktakeSearch());
            adjustmentSearchField.addValueChangeListener(valueChangeEvent -> applyAdjustmentSearch());
            searchListenersRegistered = true;
        }
        applyStocktakeSearch();
        applyAdjustmentSearch();
    }

    private void applyStocktakeSearch() {
        String term = stocktakeSearchField.getValue() == null ? "" : stocktakeSearchField.getValue().trim();
        stocktakesDl.setParameter("stocktakeSearchTerm", term.isEmpty() ? null : term);
        stocktakesDl.load();
    }

    private void applyAdjustmentSearch() {
        String term = adjustmentSearchField.getValue() == null ? "" : adjustmentSearchField.getValue().trim();
        adjustmentsDl.setParameter("adjustmentSearchTerm", term.isEmpty() ? null : term);
        adjustmentsDl.load();
    }
}
