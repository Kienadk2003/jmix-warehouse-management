package com.company.warehousemanagement.view.product;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import io.jmix.core.SaveContext;
import java.util.ArrayList;
import java.util.Set;
import com.company.warehousemanagement.service.MasterDataImportDialog;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;

@Route(value = "products", layout = MainView.class)
@ViewController(id = "Product.list")
@ViewDescriptor(path = "product-list-view.xml")
@LookupComponent("productsDataGrid")
@DialogMode(width = "64em")
public class ProductListView extends StandardListView<Product> {

    @ViewComponent
    private TextField searchField;

    @ViewComponent
    private CollectionLoader<Product> productsDl;

    private boolean searchListenerRegistered;

    @ViewComponent
    private Button importButton;

    @ViewComponent
    private Button removeButton;

    @ViewComponent
    private DataGrid<Product> productsDataGrid;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private MasterDataImportDialog masterDataImportDialog;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        productsDataGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        removeButton.setEnabled(false);
        productsDataGrid.addSelectionListener(selection -> removeButton.setEnabled(!selection.getAllSelectedItems().isEmpty()));
        removeButton.addClickListener(click -> confirmBulkDelete());
        if (importButton != null) {
            importButton.addClickListener(click -> masterDataImportDialog.open("product", () -> productsDl.load()));
        }
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        applySearch();
    }

    private void confirmBulkDelete() {
        Set<Product> selected = new java.util.LinkedHashSet<>(productsDataGrid.getSelectedItems());
        if (selected.isEmpty()) {
            removeButton.setEnabled(false);
            return;
        }
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Xác nhận xóa hàng loạt");
        dialog.setText("Bạn có chắc chắn muốn xóa " + selected.size() + " bản ghi đã chọn không? Thao tác này không thể hoàn tác.");
        dialog.setCancelable(true);
        dialog.setCancelText("Hủy");
        dialog.setConfirmText("Xóa " + selected.size() + " mục");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            try {
                SaveContext saveContext = new SaveContext();
                selected.forEach(saveContext::removing);
                dataManager.save(saveContext);
                productsDataGrid.deselectAll();
                productsDl.load();
                removeButton.setEnabled(false);
            } catch (Exception exception) {
                ConfirmDialog errorDialog = new ConfirmDialog();
                errorDialog.setHeader("Không thể xóa dữ liệu");
                errorDialog.setText("Một hoặc nhiều bản ghi có thể đang được dữ liệu khác sử dụng. Vui lòng kiểm tra dữ liệu liên quan rồi thử lại.");
                errorDialog.setConfirmText("Đóng");
                errorDialog.open();
            }
        });
        dialog.open();
    }

    private void applySearch() {
        String term = searchField.getValue() == null ? "" : searchField.getValue().trim();
        productsDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        productsDl.load();
    }
}
