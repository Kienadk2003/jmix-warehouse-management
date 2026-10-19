package com.company.warehousemanagement.view.productcategory;

import com.company.warehousemanagement.entity.ProductCategory;
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

    @ViewComponent
    private Button removeButton;

    @ViewComponent
    private DataGrid<ProductCategory> productCategoriesDataGrid;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private MasterDataImportDialog masterDataImportDialog;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        productCategoriesDataGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        removeButton.setEnabled(false);
        productCategoriesDataGrid.addSelectionListener(selection -> removeButton.setEnabled(!selection.getAllSelectedItems().isEmpty()));
        removeButton.addClickListener(click -> confirmBulkDelete());
        if (importButton != null) {
            importButton.addClickListener(click -> masterDataImportDialog.open("productcategory", () -> productCategoriesDl.load()));
        }
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        applySearch();
    }

    private void confirmBulkDelete() {
        Set<ProductCategory> selected = new java.util.LinkedHashSet<>(productCategoriesDataGrid.getSelectedItems());
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
                productCategoriesDataGrid.deselectAll();
                productCategoriesDl.load();
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
        productCategoriesDl.setParameter("searchTerm", term.isEmpty() ? null : term);
        productCategoriesDl.load();
    }
}
