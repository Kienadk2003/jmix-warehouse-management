package com.company.warehousemanagement.view.operations;

import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.security.WarehouseAuthorizationService;
import com.company.warehousemanagement.security.WarehousePermissions;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "warehouse-operations/import-export", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.importExportList")
@ViewDescriptor(path = "import-export-list-view.xml")
public class ImportExportListView extends StandardView {

    @Autowired
    private WarehouseAuthorizationService authorizationService;

    @Autowired
    private Notifications notifications;

    @ViewComponent
    private CollectionLoader<WarehouseTransaction> receiptsDl;

    @ViewComponent
    private CollectionLoader<WarehouseTransaction> exportsDl;

    @ViewComponent
    private TextField importSearchField;

    @ViewComponent
    private TextField exportSearchField;

    private boolean searchListenersRegistered;

    @ViewComponent
    private DataGrid<WarehouseTransaction> receiptsDataGrid;

    @ViewComponent
    private JmixButton createImportButton;

    @ViewComponent
    private JmixButton createExportButton;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        boolean canEditDraft = authorizationService.isAllowed(WarehousePermissions.EDIT_DRAFT);
        createImportButton.setVisible(canEditDraft);
        createExportButton.setVisible(canEditDraft);
        if (!searchListenersRegistered) {
            importSearchField.addValueChangeListener(valueChangeEvent -> applyImportSearch());
            exportSearchField.addValueChangeListener(valueChangeEvent -> applyExportSearch());
            searchListenersRegistered = true;
        }
        applyImportSearch();
        applyExportSearch();
    }

    private void applyImportSearch() {
        String term = importSearchField.getValue() == null ? "" : importSearchField.getValue().trim();
        receiptsDl.setParameter("importSearchTerm", term.isEmpty() ? null : term);
        receiptsDl.load();
    }

    private void applyExportSearch() {
        String term = exportSearchField.getValue() == null ? "" : exportSearchField.getValue().trim();
        exportsDl.setParameter("exportSearchTerm", term.isEmpty() ? null : term);
        exportsDl.load();
    }

    @Subscribe("createImportButton")
    public void onCreateImportButtonClick(ClickEvent<JmixButton> event) {
        getUI().ifPresent(ui -> ui.navigate("import-receipts/new"));
    }

    @Subscribe("openImportButton")
    public void onOpenImportButtonClick(ClickEvent<JmixButton> event) {
        WarehouseTransaction selected = receiptsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            notifications.show("Hãy chọn một phiếu nhập");
            return;
        }
        getUI().ifPresent(ui -> ui.navigate("import-receipts/" + selected.getId()));
    }

    @Subscribe("refreshImportButton")
    public void onRefreshImportButtonClick(ClickEvent<JmixButton> event) {
        receiptsDl.load();
    }
}
