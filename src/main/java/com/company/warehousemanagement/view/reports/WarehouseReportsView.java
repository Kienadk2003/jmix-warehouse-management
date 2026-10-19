package com.company.warehousemanagement.view.reports;

import com.company.warehousemanagement.entity.*;
import com.company.warehousemanagement.service.*;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
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
        content.addClassName("warehouse-reports-page");
        H2 pageTitle = new H2("Tổng quan & báo cáo kho");
        pageTitle.addClassName("warehouse-reports-title");
        Span pageSubtitle = new Span("Theo dõi nhập, xuất, chuyển kho, kiểm kê và tồn kho trong một màn hình.");
        pageSubtitle.addClassName("warehouse-reports-subtitle");
        Div heading = new Div(pageTitle, pageSubtitle);
        heading.addClassName("warehouse-reports-heading");
        content.add(heading);
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
        Button runReport = new Button("Xem báo cáo", new Icon(VaadinIcon.FILTER), e -> refresh());
        runReport.addThemeName("primary");
        runReport.addClassName("warehouse-report-submit");
        HorizontalLayout filters = new HorizontalLayout(from, to, warehouse, product, runReport);
        filters.addClassName("warehouse-report-filters");
        filters.setWidthFull();
        filters.setWrap(true);
        filters.setAlignItems(FlexComponent.Alignment.END);
        results.setPadding(false);
        results.setSpacing(true);
        results.setWidthFull();
        results.addClassName("warehouse-report-results");
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
            cards.addClassName("warehouse-report-kpis");
            Span note = new Span("Phiếu được lọc theo ngày chứng từ; kiểm kho theo ngày chốt số liệu (hoặc ngày tạo khi chưa chốt). Tồn kho tính đến hết ngày cuối kỳ.");
            note.addClassName("warehouse-report-note");
            results.add(cards, note);
            TabSheet tabs = new TabSheet();
            tabs.addClassName("warehouse-report-tabs");
            tabs.setWidthFull();
            tabs.add("Phiếu & trạng thái", summary(snapshot));
            for (var type : WarehouseTransactionType.values()) tabs.add(typeName(type), transactionLines(snapshot, type));
            tabs.add("Kiểm kho", stocktakeRows(snapshot));
            tabs.add("Tồn kho", inventoryRows(snapshot));
            results.add(tabs);
        } catch (IllegalArgumentException ex) {
            results.removeAll();
            Div error = new Div(new Icon(VaadinIcon.WARNING), new Span(ex.getMessage()));
            error.addClassName("warehouse-report-error");
            results.add(error);
        }
    }

    private VerticalLayout card(String label, long value) {
        Icon icon = new Icon(label.startsWith("Phiếu nhập") ? VaadinIcon.DOWNLOAD :
                label.startsWith("Phiếu xuất") ? VaadinIcon.UPLOAD : VaadinIcon.CLOCK);
        icon.addClassName("warehouse-report-kpi-icon");
        Span caption = new Span(label);
        caption.addClassName("warehouse-report-kpi-label");
        H2 number = new H2(Long.toString(value));
        number.addClassName("warehouse-report-kpi-value");
        VerticalLayout card = new VerticalLayout(new Div(icon), caption, number);
        card.addClassName("warehouse-report-kpi-card");
        card.setWidth("100%");
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
        statuses.setEmptyStateComponent(emptyState("Chưa có dữ liệu thống kê", "Các trạng thái phiếu sẽ xuất hiện tại đây khi có dữ liệu trong kỳ."));
        Grid<WarehouseTransaction> documents = grid();
        documents.addComponentColumn(t -> new Button(t.getDocumentNo(), e -> getUI().ifPresent(ui -> ui.navigate(route(t))))).setHeader("Mở phiếu");
        documents.addColumn(WarehouseTransaction::getDocumentDate).setHeader("Ngày chứng từ");
        documents.addColumn(t -> typeName(t.getType())).setHeader("Loại phiếu");
        documents.addColumn(t -> statusName(t.getStatus().getId())).setHeader("Trạng thái");
        documents.addColumn(t -> warehouseName(t.getSourceWarehouse())).setHeader("Kho xuất");
        documents.addColumn(t -> warehouseName(t.getDestinationWarehouse())).setHeader("Kho nhận");
        documents.setItems(snapshot.transactions());
        documents.setEmptyStateComponent(emptyState("Không có dữ liệu!", "Không tìm thấy phiếu nghiệp vụ trong khoảng thời gian đã chọn."));
        Grid<Stocktake> stocktakes = grid();
        stocktakes.addComponentColumn(s -> new Button(s.getDocumentNo(), e -> getUI().ifPresent(ui -> ui.navigate("stocktakes/" + s.getId())))).setHeader("Phiếu kiểm kho");
        stocktakes.addColumn(s -> warehouseName(s.getWarehouse())).setHeader("Kho");
        stocktakes.addColumn(s -> statusName(s.getStatus().getId())).setHeader("Trạng thái");
        stocktakes.setItems(snapshot.stocktakes());
        stocktakes.setEmptyStateComponent(emptyState("Không có dữ liệu!", "Chưa có phiếu kiểm kho trong khoảng thời gian đã chọn."));
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
        grid.setEmptyStateComponent(emptyState("Không có dữ liệu!", "Không có dòng hàng phù hợp với bộ lọc hiện tại."));
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
        grid.setEmptyStateComponent(emptyState("Không có dữ liệu!", "Chưa có dữ liệu kiểm kho phù hợp."));
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
        grid.setEmptyStateComponent(emptyState("Không có dữ liệu!", "Không có dữ liệu tồn kho cho phạm vi đã chọn."));
        return section(new Span(snapshot.inventory().size() + " cặp kho–sản phẩm • Vào/ra kho gồm chuyển kho và bút toán đảo."), grid);
    }

    private <T> Grid<T> grid() {
        Grid<T> grid = new Grid<>();
        grid.setWidthFull();
        grid.setMinHeight("260px");
        grid.setHeight("360px");
        grid.addClassName("warehouse-data-grid");
        grid.setColumnReorderingAllowed(true);
        return grid;
    }
    private Div emptyState(String title, String description) {
        // Small, CSS-drawn document stack; no fake business data is inserted.
        Div illustration = new Div();
        illustration.addClassName("warehouse-empty-illustration");
        illustration.getElement().setAttribute("role", "img");
        illustration.getElement().setAttribute("aria-label", "Minh họa danh sách trống");
        Span dots = new Span("•••");
        dots.addClassName("warehouse-empty-dots");
        illustration.add(dots);
        Div copy = new Div(new H3(title), new Span(description));
        copy.addClassName("warehouse-empty-copy");
        Div empty = new Div(illustration, copy);
        empty.addClassName("warehouse-empty-state");
        return empty;
    }
    private VerticalLayout section(com.vaadin.flow.component.Component... components) {
        VerticalLayout layout = new VerticalLayout(components);
        layout.addClassName("warehouse-report-section");
        layout.setPadding(false);
        layout.setSpacing(true);
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
