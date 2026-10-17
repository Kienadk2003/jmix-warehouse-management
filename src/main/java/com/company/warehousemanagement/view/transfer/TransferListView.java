package com.company.warehousemanagement.view.transfer;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import org.springframework.beans.factory.annotation.Autowired;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "transfers", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.transferList")
@ViewDescriptor(path = "transfer-list-view.xml")
@LookupComponent("transfersDataGrid")
@DialogMode(width = "80em")
public class TransferListView
        extends StandardListView<WarehouseTransaction> {

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @ViewComponent
    private JmixButton createButton;

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<WarehouseTransaction> transfersDl;

    @ViewComponent
    private CollectionContainer<WarehouseTransaction> transfersDc;

    @ViewComponent
    private com.vaadin.flow.component.grid.Grid<WarehouseTransaction> transfersDataGrid;

    @ViewComponent
    private VerticalLayout transfersEmptyState;

    @ViewComponent
    private Span transfersEmptyTitle;

    @ViewComponent
    private Span transfersEmptyHint;

    private boolean searchListenerRegistered;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        createButton.setVisible(
                authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT)
        );
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        applySearch();
    }

    private void refreshEmptyState() {
        boolean empty = transfersDc.getItems().isEmpty();
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        transfersDataGrid.setVisible(!empty);
        transfersEmptyState.setVisible(empty);
        transfersEmptyTitle.setText(messagesText(!term.isEmpty() ? "emptyState.noMatch.title" : "emptyState.title"));
        transfersEmptyHint.setText(messagesText(!term.isEmpty() ? "emptyState.noMatch.hint" : "emptyState.hint"));
    }

    @ViewComponent
    private io.jmix.flowui.view.MessageBundle messageBundle;

    private String messagesText(String key) {
        return messageBundle.getMessage(key);
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        transfersDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        transfersDl.load();
        refreshEmptyState();
    }
}