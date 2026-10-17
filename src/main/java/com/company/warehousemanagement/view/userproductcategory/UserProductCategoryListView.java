package com.company.warehousemanagement.view.userproductcategory;

import com.company.warehousemanagement.entity.UserProductCategory;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "user-product-categories", layout = MainView.class)
@ViewController(id = "UserProductCategory.list")
@ViewDescriptor(path = "user-product-category-list-view.xml")
@LookupComponent("userProductCategoriesDataGrid")
@DialogMode(width = "64em")
public class UserProductCategoryListView extends StandardListView<UserProductCategory> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<UserProductCategory> userProductCategoriesDl;

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
        userProductCategoriesDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        userProductCategoriesDl.load();
    }
}
