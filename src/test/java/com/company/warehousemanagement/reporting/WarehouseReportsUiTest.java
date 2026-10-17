package com.company.warehousemanagement.reporting;

import com.company.warehousemanagement.WarehouseManagementApplication;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.view.reports.WarehouseReportsView;
import com.company.warehousemanagement.view.export.ExportDetailView;
import com.company.warehousemanagement.view.transfer.TransferDetailView;
import com.company.warehousemanagement.view.adjustment.AdjustmentDetailView;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.testassist.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import static org.assertj.core.api.Assertions.assertThat;

@UiTest
@SpringBootTest(classes = {WarehouseManagementApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class WarehouseReportsUiTest {
    @Autowired ViewNavigators navigators;

    @Test void reportViewLoadsFiltersMetricsAndTables() {
        navigators.view(UiTestUtils.getCurrentView(), WarehouseReportsView.class).navigate();
        assertThat((Object) UiTestUtils.getCurrentView()).isInstanceOf(WarehouseReportsView.class);
        VerticalLayout content = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "content");
        assertThat(content.getComponentCount()).isGreaterThan(3);
        DatePicker from = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "fromDate");
        DatePicker to = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "toDate");
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(from.getValue()).isEqualTo(today.withDayOfMonth(1));
        assertThat(to.getValue()).isEqualTo(today.with(TemporalAdjusters.lastDayOfMonth()));
    }

    @Test void draftEditorsIncludeDocumentsPanel() {
        navigators.detailView(UiTestUtils.getCurrentView(), WarehouseTransaction.class).withViewClass(ExportDetailView.class).newEntity().navigate();
        VerticalLayout host = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "documentsHost");
        assertThat(host.getComponentCount()).isGreaterThan(1);
        navigators.detailView(UiTestUtils.getCurrentView(), WarehouseTransaction.class).withViewClass(TransferDetailView.class).newEntity().navigate();
        host = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "documentsHost");
        assertThat(host.getComponentCount()).isGreaterThan(1);
        navigators.detailView(UiTestUtils.getCurrentView(), WarehouseTransaction.class).withViewClass(AdjustmentDetailView.class).newEntity().navigate();
        host = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "documentsHost");
        assertThat(host.getComponentCount()).isGreaterThan(1);
    }
}
