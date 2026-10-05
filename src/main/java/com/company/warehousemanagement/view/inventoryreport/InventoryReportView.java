package com.company.warehousemanagement.view.inventoryreport;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.service.InventoryReportService;
import com.company.warehousemanagement.service.InventoryReportService.InventoryReportRow;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.router.Route;
import io.jmix.core.entity.KeyValueEntity;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.model.KeyValueCollectionContainer;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import com.vaadin.flow.component.button.Button;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Route(
        value = "inventory-report",
        layout = MainView.class
)
@ViewController(
        id = "Inventory.report"
)
@ViewDescriptor(
        path = "inventory-report-view.xml"
)
public class InventoryReportView extends StandardView {

    @Autowired
    private InventoryReportService inventoryReportService;

    @ViewComponent
    private EntityComboBox<Warehouse> warehouseField;

    @ViewComponent
    private EntityComboBox<Product> productField;

    @ViewComponent
    private TypedDatePicker<LocalDate> fromDateField;

    @ViewComponent
    private TypedDatePicker<LocalDate> toDateField;

    @ViewComponent
    private KeyValueCollectionContainer reportDc;

    @ViewComponent
    private Button searchButton;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {

        LocalDate today = LocalDate.now();

        fromDateField.setTypedValue(
                today.withDayOfMonth(1)
        );

        toDateField.setTypedValue(today);

        reportDc.getMutableItems().clear();
    }

    @Subscribe(
            id = "searchButton",
            subject = "clickListener"
    )
    public void onSearchButtonClick(
            ClickEvent<Button> event) {

        Warehouse warehouse =
                warehouseField.getValue();

        Product product =
                productField.getValue();

        LocalDate fromDate =
                fromDateField.getTypedValue();

        LocalDate toDate =
                toDateField.getTypedValue();

        try {

            List<InventoryReportRow> rows =
                    inventoryReportService.buildReport(
                            warehouse != null
                                    ? warehouse.getId()
                                    : null,
                            product != null
                                    ? product.getId()
                                    : null,
                            fromDate,
                            toDate
                    );

            List<KeyValueEntity> entities =
                    new ArrayList<>();

            for (InventoryReportRow row : rows) {

                KeyValueEntity entity =
                        reportDc.createEntity();

                entity.setValue(
                        "warehouse",
                        row.warehouse()
                );

                entity.setValue(
                        "product",
                        row.product()
                );

                entity.setValue(
                        "opening",
                        row.opening()
                );

                entity.setValue(
                        "inbound",
                        row.inbound()
                );

                entity.setValue(
                        "outbound",
                        row.outbound()
                );

                entity.setValue(
                        "adjustmentIn",
                        row.adjustmentIn()
                );

                entity.setValue(
                        "adjustmentOut",
                        row.adjustmentOut()
                );

                entity.setValue(
                        "closing",
                        row.closing()
                );

                entities.add(entity);
            }

            reportDc.setItems(entities);

            Notification.show(
                    "Inventory report loaded successfully",
                    2500,
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
}