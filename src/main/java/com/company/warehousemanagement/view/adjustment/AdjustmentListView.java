package com.company.warehousemanagement.view.adjustment;

import com.company.warehousemanagement.entity.WarehouseTransaction;
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

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @ViewComponent
    private JmixButton createButton;

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<WarehouseTransaction> adjustmentsDl;

    private boolean searchListenerRegistered;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        createButton.setVisible(
                authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT)
        );
        applySearch();
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        adjustmentsDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        adjustmentsDl.load();
    }
}