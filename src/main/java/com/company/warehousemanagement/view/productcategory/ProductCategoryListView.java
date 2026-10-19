package com.company.warehousemanagement.view.productcategory;

import com.company.warehousemanagement.entity.ProductCategory;
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

@Route(value = "product-categories", layout = MainView.class)
@ViewController(id = "ProductCategory.list")
@ViewDescriptor(path = "product-category-list-view.xml")
@LookupComponent("productCategoriesDataGrid")
@DialogMode(width = "64em")
public class ProductCategoryListView extends StandardListView<ProductCategory> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<ProductCategory> productCategoriesDl;

    private boolean searchListenerRegistered;

    @ViewComponent
    private Button importButton;

    @Autowired
    private MasterDataImportDialog masterDataImportDialog;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (importButton != null) {
            importButton.addClickListener(click -> masterDataImportDialog.open("productcategory", () -> productCategoriesDl.load()));
        }
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        applySearch();
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        productCategoriesDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        productCategoriesDl.load();
    }
}
