package com.company.warehousemanagement.view.user;

import com.company.warehousemanagement.entity.User;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "users", layout = MainView.class)
@ViewController(id = "User.list")
@ViewDescriptor(path = "user-list-view.xml")
@LookupComponent("usersDataGrid")
@DialogMode(width = "64em")
public class UserListView extends StandardListView<User> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<User> usersDl;

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
        usersDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        usersDl.load();
    }
}
