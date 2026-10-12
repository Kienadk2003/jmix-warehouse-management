package com.company.warehousemanagement.view.reports;

import com.company.warehousemanagement.entity.*;
import com.company.warehousemanagement.service.*;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Route(value = "warehouse-reports", layout = MainView.class)
@ViewController(id = "Warehouse.reports")
@ViewDescriptor(path = "warehouse-reports-view.xml")
public class WarehouseReportsView extends StandardView {
    @Autowired private WarehouseReportService reports;
    @Autowired private DataManager data;
    @ViewComponent private VerticalLayout content;
    private final DatePicker from = new DatePicker("Từ ngày");
    private final DatePicker to = new DatePicker("Đến ngày");
    private final ComboBox<Warehouse> warehouse = new ComboBox<>("Kho");
    private final TextField product = new TextField("Mã sản phẩm");
    private final VerticalLayout results = new VerticalLayout();

    @Subscribe
    public void onInit(InitEvent event) {
        content.add(new H2("Tổng quan & báo cáo kho"), new Span("Chọn khoảng ngày, kho và mã sản phẩm để xem cùng một phạm vi dữ liệu."));
        var today = reports.today();
        from.setId("fromDate");
        to.setId("toDate");
        from.setValue(today.withDayOfMonth(1));
        to.setValue(today.with(TemporalAdjusters.lastDayOfMonth()));
        warehouse.setItems(data.load(Warehouse.class).all().list());
        warehouse.setItemLabelGenerator(w -> w.getCode() + " — " + w.getName());
        warehouse.setClearButtonVisible(true);
        warehouse.setPlaceholder("Tất cả kho được phân quyền");
        product.setPlaceholder("Nhập chính xác mã sản phẩm");
        product.setClearButtonVisible(true);
        product.setMaxLength(50);
        HorizontalLayout filters = new HorizontalLayout(from, to, warehouse, product, new Button("Xem báo cáo", e -> refresh()));
        filters.setWidthFull();
        filters.setWrap(true);
        filters.setAlignItems(FlexComponent.Alignment.END);
        results.setPadding(false);
        results.setWidthFull();
        content.add(filters, results);
        refresh();
    }

    private void refresh() {
        try {
            var snapshot = reports.build(warehouse.getValue() == null ? null : warehouse.getValue().getId(),
                    product.getValue(), from.getValue(), to.getValue());
            results.removeAll();
            HorizontalLayout cards = new HorizontalLayout(
                    card("Phiếu nhập đã ghi sổ", snapshot.posted(WarehouseTransactionType.IMPORT)),
                    card("Phiếu xuất đã ghi sổ", snapshot.posted(WarehouseTransactionType.EXPORT)),
                    card("Phiếu chờ duyệt trong kỳ", snapshot.pending()));
            cards.setWidthFull();
            cards.setWrap(true);
            results.add(cards, new Span("Phiếu lọc theo ngày chứng từ; kiểm kho theo ngày chốt số liệu (hoặc ngày tạo khi chưa chốt). Tồn kho tính theo thời điểm ghi sổ, đến hết ngày cuối kỳ. Số lượng được xem riêng theo đơn vị tính."));
            TabSheet tabs = new TabSheet();
            tabs.setWidthFull();
            tabs.add("Phiếu & trạng thái", summary(snapshot));
            for (var type : WarehouseTransactionType.values()) tabs.add(typeName(type), transactionLines(snapshot, type));
            tabs.add("Kiểm kho", stocktakeRows(snapshot));
            tabs.add("Tồn kho", inventoryRows(snapshot));
            results.add(tabs);
        } catch (IllegalArgumentException ex) {
            results.removeAll();
            Notification.show(ex.getMessage());
        }
    }

    private VerticalLayout card(String label, long value) {
        VerticalLayout card = new VerticalLayout(new Span(label), new H2(Long.toString(value)));
        card.addClassName("warehouse-dashboard-panel");
        card.setWidth("240px");
        return card;
    }

    private VerticalLayout summary(WarehouseReportService.Snapshot snapshot) {
        Grid<StatusRow> statuses = grid();
        statuses.addColumn(StatusRow::type).setHeader("Nghiệp vụ");
        statuses.addColumn(r -> statusName(r.status())).setHeader("Trạng thái");
        statuses.addColumn(StatusRow::count).setHeader("Số phiếu");
        List<StatusRow> rows = new ArrayList<>();
        for (var type : WarehouseTransactionType.values()) for (var status : WarehouseTransactionStatus.values()) {
            long count = snapshot.transactions().stream().filter(t -> t.getType() == type && t.getStatus() == status).count();
            if (count > 0) rows.add(new StatusRow(typeName(type), status.getId(), count));
        }
        for (var status : StocktakeStatus.values()) {
            long count = snapshot.stocktakes().stream().filter(t -> t.getStatus() == status).count();
            if (count > 0) rows.add(new StatusRow("Kiểm kho", status.getId(), count));
        }
        statuses.setItems(rows);
        Grid<WarehouseTransaction> documents = grid();
        documents.addComponentColumn(t -> new Button(t.getDocumentNo(), e -> getUI().ifPresent(ui -> ui.navigate(route(t))))).setHeader("Mở phiếu");
        documents.addColumn(WarehouseTransaction::getDocumentDate).setHeader("Ngày chứng từ");
        documents.addColumn(t -> typeName(t.getType())).setHeader("Loại phiếu");
        documents.addColumn(t -> statusName(t.getStatus().getId())).setHeader("Trạng thái");
        documents.addColumn(t -> warehouseName(t.getSourceWarehouse())).setHeader("Kho xuất");
        documents.addColumn(t -> warehouseName(t.getDestinationWarehouse())).setHeader("Kho nhận");
        documents.setItems(snapshot.transactions());
        Grid<Stocktake> stocktakes = grid();
        stocktakes.addComponentColumn(s -> new Button(s.getDocumentNo(), e -> getUI().ifPresent(ui -> ui.navigate("stocktakes/" + s.getId())))).setHeader("Phiếu kiểm kho");
        stocktakes.addColumn(s -> warehouseName(s.getWarehouse())).setHeader("Kho");
        stocktakes.addColumn(s -> statusName(s.getStatus().getId())).setHeader("Trạng thái");
        stocktakes.setItems(snapshot.stocktakes());
        return section(new Span(snapshot.transactions().size() + " phiếu nghiệp vụ • " + snapshot.stocktakes().size() + " phiếu kiểm kho"), new H3("Thống kê theo trạng thái"), statuses, new H3("Danh sách phiếu"), documents, stocktakes);
    }

    private VerticalLayout transactionLines(WarehouseReportService.Snapshot snapshot, WarehouseTransactionType type) {
        Grid<TransactionItem> grid = grid();
        grid.addColumn(i -> i.getTransaction().getDocumentNo()).setHeader("Số phiếu");
        grid.addColumn(i -> i.getTransaction().getDocumentDate()).setHeader("Ngày");
        grid.addColumn(i -> statusName(i.getTransaction().getStatus().getId())).setHeader("Trạng thái");
        grid.addColumn(i -> warehouseName(i.getTransaction().getSourceWarehouse())).setHeader("Kho xuất");
        grid.addColumn(i -> warehouseName(i.getTransaction().getDestinationWarehouse())).setHeader("Kho nhận");
        grid.addColumn(i -> i.getProduct().getCode()).setHeader("Mã sản phẩm");
        grid.addColumn(i -> i.getProduct().getName()).setHeader("Sản phẩm");
        grid.addColumn(TransactionItem::getQuantity).setHeader("Số lượng");
        grid.addColumn(i -> i.getUnit().getName()).setHeader("Đơn vị");
        var rows = snapshot.lines().stream().filter(i -> i.getTransaction().getType() == type).toList();
        grid.setItems(rows);
        return section(new Span(rows.size() + " dòng hàng • bao gồm mọi trạng thái; chỉ phiếu đã ghi sổ ảnh hưởng tồn kho."), grid);
    }

    private VerticalLayout stocktakeRows(WarehouseReportService.Snapshot snapshot) {
        Grid<StocktakeItem> grid = grid();
        grid.addColumn(i -> i.getStocktake().getDocumentNo()).setHeader("Phiếu kiểm kho");
        grid.addColumn(i -> warehouseName(i.getStocktake().getWarehouse())).setHeader("Kho");
        grid.addColumn(i -> statusName(i.getStocktake().getStatus().getId())).setHeader("Trạng thái");
        grid.addColumn(i -> i.getProduct().getCode()).setHeader("Mã sản phẩm");
        grid.addColumn(i -> i.getProduct().getBaseUnit().getName()).setHeader("Đơn vị");
        grid.addColumn(StocktakeItem::getBookQuantity).setHeader("Sổ sách");
        grid.addColumn(i -> i.getCountedQuantity() == null ? "Chưa kiểm" : i.getCountedQuantity().toPlainString()).setHeader("Thực đếm");
        grid.addColumn(StocktakeItem::getVarianceQuantity).setHeader("Chênh lệch");
        grid.setItems(snapshot.counts());
        return section(new Span(snapshot.counts().size() + " dòng kiểm kho"), grid);
    }

    private VerticalLayout inventoryRows(WarehouseReportService.Snapshot snapshot) {
        Grid<InventoryReportService.InventoryReportRow> grid = grid();
        grid.addColumn(r -> warehouseName(r.warehouse())).setHeader("Kho");
        grid.addColumn(r -> r.product().getCode()).setHeader("Mã sản phẩm");
        grid.addColumn(r -> r.product().getName()).setHeader("Sản phẩm");
        grid.addColumn(r -> r.product().getBaseUnit().getName()).setHeader("Đơn vị");
        grid.addColumn(InventoryReportService.InventoryReportRow::opening).setHeader("Đầu kỳ");
        grid.addColumn(InventoryReportService.InventoryReportRow::inbound).setHeader("Vào kho");
        grid.addColumn(InventoryReportService.InventoryReportRow::outbound).setHeader("Ra kho");
        grid.addColumn(InventoryReportService.InventoryReportRow::adjustmentIn).setHeader("Điều chỉnh tăng");
        grid.addColumn(InventoryReportService.InventoryReportRow::adjustmentOut).setHeader("Điều chỉnh giảm");
        grid.addColumn(InventoryReportService.InventoryReportRow::closing).setHeader("Cuối kỳ");
        grid.setItems(snapshot.inventory());
        return section(new Span(snapshot.inventory().size() + " cặp kho–sản phẩm • Vào/ra kho gồm chuyển kho và bút toán đảo."), grid);
    }

    private <T> Grid<T> grid() {
        Grid<T> grid = new Grid<>();
        grid.setWidthFull();
        grid.setHeight("360px");
        grid.setColumnReorderingAllowed(true);
        return grid;
    }
    private VerticalLayout section(com.vaadin.flow.component.Component... components) {
        VerticalLayout layout = new VerticalLayout(components);
        layout.setPadding(false);
        layout.setWidthFull();
        return layout;
    }
    private String warehouseName(Warehouse value) { return value == null ? "—" : value.getCode() + " — " + value.getName(); }
    private String typeName(WarehouseTransactionType type) {
        return switch (type) { case IMPORT -> "Nhập kho"; case EXPORT -> "Xuất kho"; case TRANSFER -> "Chuyển kho"; case ADJUSTMENT -> "Điều chỉnh"; };
    }
    private String route(WarehouseTransaction t) {
        return (switch (t.getType()) { case IMPORT -> "import-receipts/"; case EXPORT -> "export-issues/"; case TRANSFER -> "transfers/"; case ADJUSTMENT -> "adjustments/"; }) + t.getId();
    }
    private String statusName(String status) {
        return switch (status) {
            case "DRAFT" -> "Nháp"; case "PENDING_APPROVAL", "REVIEWING" -> "Chờ duyệt";
            case "APPROVED" -> "Đã duyệt"; case "REJECTED" -> "Từ chối"; case "POSTED" -> "Đã ghi sổ";
            case "CONFIRMED" -> "Đã xác nhận"; case "CANCELLED" -> "Đã hủy"; case "REVERSED" -> "Đã đảo";
            case "COUNTING" -> "Đang kiểm"; default -> status;
        };
    }
    private record StatusRow(String type, String status, long count) { }
}
