package com.company.warehousemanagement.view.transactionitem;

import com.company.warehousemanagement.entity.TransactionItem;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.AdjustmentDirection;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import com.company.warehousemanagement.service.AvailableStockService;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.StandardDetailView.ValidationEvent;
import io.jmix.flowui.view.StandardDetailView.InitEntityEvent;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

@Route(value = "transaction-items/:id", layout = MainView.class)
@ViewController(id = "TransactionItem.detail")
@ViewDescriptor(path = "transaction-item-detail-view.xml")
@EditedEntityContainer("transactionItemDc")
public class TransactionItemDetailView extends StandardDetailView<TransactionItem> {

    @Autowired
    private AvailableStockService availableStockService;

    @ViewComponent
    private TypedTextField<String> availableStockField;

    @ViewComponent
    private EntityComboBox<Product> productField;

    @Subscribe
    public void onInitEntity(InitEntityEvent<TransactionItem> event) {
        event.getEntity().setQuantity(null);
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        productField.setItemLabelGenerator(this::productLabel);
        refreshAvailableStock();
    }

    @Subscribe(id = "transactionItemDc", target = Target.DATA_CONTAINER)
    public void onTransactionItemDcItemPropertyChange(
            InstanceContainer.ItemPropertyChangeEvent<TransactionItem> event) {
        refreshAvailableStock();
    }

    @Subscribe
    public void onValidation(ValidationEvent event) {
        TransactionItem item = getEditedEntity();
        WarehouseTransaction transaction = item.getTransaction();
        if (!reducesStock(transaction) || item.getProduct() == null || item.getQuantity() == null
                || transaction.getSourceWarehouse() == null) {
            return;
        }
        try {
            availableStockService.validateAvailableStock(
                    transaction.getSourceWarehouse().getId(),
                    item.getProduct().getId(),
                    item.getQuantity());
        } catch (WarehouseBusinessException exception) {
            event.getErrors().add(exception.getMessage());
        }
    }

    private void refreshAvailableStock() {
        TransactionItem item = getEditedEntity();
        WarehouseTransaction transaction = item.getTransaction();
        if (transaction == null || transaction.getSourceWarehouse() == null) {
            availableStockField.setTypedValue("Chọn kho nguồn trước");
            return;
        }
        if (item.getProduct() == null || item.getProduct().getId() == null) {
            availableStockField.setTypedValue("Chọn sản phẩm để xem tồn");
            return;
        }
        BigDecimal available = availableStockService.getAvailableStock(
                transaction.getSourceWarehouse().getId(), item.getProduct().getId());
        availableStockField.setTypedValue(available.stripTrailingZeros().toPlainString());
    }

    /**
     * Product labels are customized only in warehouse operation item selection.
     * The global Product instance name is intentionally unchanged so that
     * the Import Receipt screen keeps its existing behavior.
     */
    private String productLabel(Product product) {
        if (product == null) {
            return "";
        }
        String code = product.getCode() == null ? "" : product.getCode().trim();
        String name = product.getName() == null ? "" : product.getName().trim();
        String categoryName = product.getCategory() == null
                || product.getCategory().getName() == null
                ? "Chưa phân loại"
                : product.getCategory().getName().trim();
        String productName = code.isEmpty() ? name : (name.isEmpty() ? code : code + " - " + name);
        return productName + " | Danh mục: " + categoryName;
    }

    private boolean reducesStock(WarehouseTransaction transaction) {
        if (transaction == null) {
            return false;
        }
        return transaction.getType() == WarehouseTransactionType.EXPORT
                || transaction.getType() == WarehouseTransactionType.TRANSFER
                || (transaction.getType() == WarehouseTransactionType.ADJUSTMENT
                && transaction.getAdjustmentDirection() == AdjustmentDirection.OUT);
    }
}
