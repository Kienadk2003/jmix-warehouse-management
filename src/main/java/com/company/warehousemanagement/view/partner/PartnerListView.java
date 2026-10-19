package com.company.warehousemanagement.view.partner;

import com.company.warehousemanagement.entity.Partner;
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

@Route(value = "partners", layout = MainView.class)
@ViewController(id = "Partner.list")
@ViewDescriptor(path = "partner-list-view.xml")
@LookupComponent("partnersDataGrid")
@DialogMode(width = "64em")
public class PartnerListView extends StandardListView<Partner> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<Partner> partnersDl;

    private boolean searchListenerRegistered;

    @ViewComponent
    private Button importButton;

    @Autowired
    private MasterDataImportDialog masterDataImportDialog;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (importButton != null) {
            importButton.addClickListener(click -> masterDataImportDialog.open("partner", () -> partnersDl.load()));
        }
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        applySearch();
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        partnersDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        partnersDl.load();
    }
}
