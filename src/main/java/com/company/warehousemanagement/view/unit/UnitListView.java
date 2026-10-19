package com.company.warehousemanagement.view.unit;

import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.button.Button;
import com.company.warehousemanagement.service.MasterDataImportDialog;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "units", layout = MainView.class)
@ViewController(id = "Unit.list")
@ViewDescriptor(path = "unit-list-view.xml")
@LookupComponent("unitsDataGrid")
@DialogMode(width = "64em")
public class UnitListView extends StandardListView<Unit> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<Unit> unitsDl;

    private boolean searchListenerRegistered;

    @ViewComponent
    private Button importButton;

    @Autowired
    private MasterDataImportDialog masterDataImportDialog;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (importButton != null) {
            importButton.addClickListener(click -> masterDataImportDialog.open("unit", () -> unitsDl.load()));
        }
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        applySearch();
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        unitsDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        unitsDl.load();
    }
}
