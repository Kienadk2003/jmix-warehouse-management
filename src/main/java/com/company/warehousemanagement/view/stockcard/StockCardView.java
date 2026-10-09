package com.company.warehousemanagement.view.stockcard;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.service.StockCardService;
import com.company.warehousemanagement.service.StockCardService.StockCardResult;
import com.company.warehousemanagement.service.StockCardService.StockCardRow;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.router.Route;
import io.jmix.core.entity.KeyValueEntity;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.KeyValueCollectionContainer;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

@Route(
        value = "stock-card",
        layout = MainView.class
)
@ViewController(
        id = "Inventory.stockCard"
)
@ViewDescriptor(
        path = "stock-card-view.xml"
)
public class StockCardView extends StandardView {

    @Autowired
    private StockCardService stockCardService;

    @ViewComponent
    private EntityComboBox<Warehouse> warehouseField;

    @ViewComponent
    private EntityComboBox<Product> productField;

    @ViewComponent
    private TypedDatePicker<LocalDate> fromDateField;

    @ViewComponent
    private TypedDatePicker<LocalDate> toDateField;

    @ViewComponent
    private TypedTextField<String> openingStockField;

    @ViewComponent
    private TypedTextField<String> closingStockField;

    @ViewComponent
    private TypedTextField<String> currentInventoryField;

    @ViewComponent
    private KeyValueCollectionContainer stockCardDc;

    @ViewComponent
    private JmixButton searchButton;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {

        LocalDate today = LocalDate.now();

        fromDateField.setTypedValue(
                today.withDayOfMonth(1)
        );

        toDateField.setTypedValue(today);

        openingStockField.setTypedValue("0");
        closingStockField.setTypedValue("0");
        currentInventoryField.setTypedValue("0");
    }

    @Subscribe(
            id = "searchButton",
            subject = "clickListener"
    )
    public void onSearchButtonClick(
            ClickEvent<JmixButton> event) {

        Warehouse warehouse =
                warehouseField.getValue();

        Product product =
                productField.getValue();

        LocalDate fromDate =
                fromDateField.getTypedValue();

        LocalDate toDate =
                toDateField.getTypedValue();

        if (warehouse == null) {

            Notification.show(
                    "Vui lòng chọn kho",
                    3000,
                    Position.TOP_END
            );

            return;
        }

        if (product == null) {

            Notification.show(
                    "Vui lòng chọn sản phẩm",
                    3000,
                    Position.TOP_END
            );

            return;
        }

        try {

            StockCardResult result =
                    stockCardService.buildStockCard(
                            warehouse.getId(),
                            product.getId(),
                            fromDate,
                            toDate
                    );

            openingStockField.setTypedValue(
                    formatQuantity(result.openingStock())
            );

            closingStockField.setTypedValue(
                    formatQuantity(result.closingStock())
            );

            currentInventoryField.setTypedValue(
                    formatQuantity(result.currentInventory())
            );

            stockCardDc
                    .getMutableItems()
                    .clear();

            for (StockCardRow row : result.rows()) {

                KeyValueEntity entity =
                        stockCardDc.createEntity();

                entity.setValue(
                        "movementId",
                        row.movementId()
                );

                entity.setValue(
                        "movementTime",
                        row.occurredAt().toString()
                );

                entity.setValue(
                        "documentNo",
                        row.documentNo()
                );

                entity.setValue(
                        "transactionType",
                        row.transactionType()
                );

                entity.setValue(
                        "movementType",
                        row.movementType()
                );

                entity.setValue(
                        "signedQuantity",
                        row.signedQuantity()
                );

                entity.setValue(
                        "runningBalance",
                        row.runningBalance()
                );

                stockCardDc
                        .getMutableItems()
                        .add(entity);
            }

            Notification.show(
                    "Đã tải thẻ kho thành công",
                    2000,
                    Position.TOP_END
            );

        } catch (IllegalArgumentException e) {

            Notification.show(
                    e.getMessage(),
                    4000,
                    Position.TOP_END
            );
        }
    }

    private String formatQuantity(BigDecimal value) {

        if (value == null) {
            return "0";
        }

        return value
                .stripTrailingZeros()
                .toPlainString();
    }
}
