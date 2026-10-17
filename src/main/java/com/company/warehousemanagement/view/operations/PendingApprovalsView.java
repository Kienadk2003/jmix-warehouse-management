package com.company.warehousemanagement.view.operations;

import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeStatus;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import com.company.warehousemanagement.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.View.BeforeShowEvent;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One unified approval queue for import, export, transfer, adjustment and stocktake.
 * The view only lists pending items; approval/rejection remains in each existing detail view.
 */
@Route(value = "warehouse-operations/pending-approvals", layout = MainView.class)
@ViewController(id = "WarehouseTransaction.pendingApprovals")
@ViewDescriptor(path = "pending-approvals-view.xml")
public class PendingApprovalsView extends StandardView {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private DataManager dataManager;

    @ViewComponent
    private VerticalLayout pendingApprovalsGridHost;

    @ViewComponent
    private JmixButton openSelectedButton;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private TypedTextField<String> searchField;

    @ViewComponent
    private VerticalLayout pendingEmptyState;

    @ViewComponent
    private Span pendingEmptyTitle;

    @ViewComponent
    private Span pendingEmptyHint;

    private Grid<PendingApprovalRow> approvalsGrid;
    private GridListDataView<PendingApprovalRow> approvalsDataView;
    private boolean searchListenerRegistered;
    private int totalRows;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        ensureGridCreated();
        if (!searchListenerRegistered) {
            searchField.addValueChangeListener(valueChangeEvent -> applySearch());
            searchListenerRegistered = true;
        }
        reloadPendingApprovals();
    }

    @Subscribe("openSelectedButton")
    public void onOpenSelectedButtonClick(ClickEvent<JmixButton> event) {
        if (approvalsGrid == null) {
            Notification.show(messageBundle.getMessage("notification.notReady"));
            return;
        }

        PendingApprovalRow selected = approvalsGrid.getSelectedItems().stream().findFirst().orElse(null);
        if (selected == null) {
            Notification.show(messageBundle.getMessage("notification.selectOne"));
            return;
        }

        getUI().ifPresent(ui -> ui.navigate(selected.route() + "/" + selected.entityId()));
    }

    @Subscribe("refreshButton")
    public void onRefreshButtonClick(ClickEvent<JmixButton> event) {
        reloadPendingApprovals();
        Notification.show(messageBundle.getMessage("notification.refreshed"));
    }

    private void ensureGridCreated() {
        if (approvalsGrid != null) {
            return;
        }

        approvalsGrid = new Grid<>();
        approvalsGrid.setWidthFull();
        approvalsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        approvalsGrid.asSingleSelect().addValueChangeListener(event ->
                openSelectedButton.setEnabled(event.getValue() != null));
        approvalsGrid.addColumn(PendingApprovalRow::documentNo)
                .setHeader(messageBundle.getMessage("column.documentNo")).setAutoWidth(true).setFlexGrow(1);
        approvalsGrid.addColumn(PendingApprovalRow::typeLabel)
                .setHeader(messageBundle.getMessage("column.documentType")).setAutoWidth(true).setFlexGrow(1);
        approvalsGrid.addColumn(PendingApprovalRow::dateLabel)
                .setHeader(messageBundle.getMessage("column.documentDate")).setAutoWidth(true).setFlexGrow(1);
        approvalsGrid.addColumn(PendingApprovalRow::statusLabel)
                .setHeader(messageBundle.getMessage("column.status")).setAutoWidth(true).setFlexGrow(1);

        pendingApprovalsGridHost.setWidthFull();
        pendingApprovalsGridHost.add(approvalsGrid);
    }

    private void reloadPendingApprovals() {
        List<PendingApprovalRow> rows = new ArrayList<>();

        List<WarehouseTransaction> transactions = dataManager.load(WarehouseTransaction.class)
                .query("select e from WarehouseTransaction e where e.status = :status")
                .parameter("status", WarehouseTransactionStatus.PENDING_APPROVAL)
                .list();

        for (WarehouseTransaction transaction : transactions) {
            String route = routeFor(transaction.getType());
            if (route == null || transaction.getId() == null) {
                continue;
            }

            Object dateValue = transaction.getDocumentDate() != null
                    ? transaction.getDocumentDate()
                    : transaction.getCreatedDate();
            rows.add(new PendingApprovalRow(
                    transaction.getId().toString(),
                    safeText(transaction.getDocumentNo()),
                    labelFor(transaction.getType()),
                    formatDate(dateValue),
                    messageBundle.getMessage("status.pending"),
                    route,
                    toInstant(dateValue)
            ));
        }

        List<Stocktake> stocktakes = dataManager.load(Stocktake.class)
                .query("select e from Stocktake e where e.status = :status")
                .parameter("status", StocktakeStatus.PENDING_APPROVAL)
                .list();

        for (Stocktake stocktake : stocktakes) {
            if (stocktake.getId() == null) {
                continue;
            }
            Object dateValue = stocktake.getCreatedDate();
            rows.add(new PendingApprovalRow(
                    stocktake.getId().toString(),
                    safeText(stocktake.getDocumentNo()),
                    messageBundle.getMessage("type.stocktake"),
                    formatDate(dateValue),
                    messageBundle.getMessage("status.pending"),
                    "stocktakes",
                    toInstant(dateValue)
            ));
        }

        rows.sort(Comparator.comparing(PendingApprovalRow::sortDate).reversed());
        totalRows = rows.size();
        if (approvalsGrid != null) {
            approvalsDataView = approvalsGrid.setItems(rows);
            applySearch();
        }
        if (openSelectedButton != null) {
            openSelectedButton.setEnabled(false);
        }
    }

    /** In-memory search over the rows that are already loaded; the approval workflow is untouched. */
    private void applySearch() {
        if (approvalsDataView == null) {
            return;
        }
        String term = searchField.getTypedValue() == null ? "" : searchField.getTypedValue().trim().toLowerCase();
        if (term.isEmpty()) {
            approvalsDataView.setFilter(row -> true);
        } else {
            approvalsDataView.setFilter(row -> contains(row.documentNo(), term)
                    || contains(row.typeLabel(), term)
                    || contains(row.dateLabel(), term)
                    || contains(row.statusLabel(), term));
        }

        boolean noRows = approvalsDataView.getItemCount() == 0;
        approvalsGrid.setVisible(!noRows);
        pendingEmptyState.setVisible(noRows);
        boolean filtered = !term.isEmpty();
        pendingEmptyTitle.setText(messageBundle.getMessage(filtered ? "emptyState.noMatch.title" : "emptyState.title"));
        pendingEmptyHint.setText(messageBundle.getMessage(filtered ? "emptyState.noMatch.hint" : "emptyState.hint"));
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    private static String routeFor(WarehouseTransactionType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case IMPORT -> "import-receipts";
            case EXPORT -> "export-issues";
            case TRANSFER -> "transfers";
            case ADJUSTMENT -> "adjustments";
            default -> null;
        };
    }

    private String labelFor(WarehouseTransactionType type) {
        if (type == null) {
            return messageBundle.getMessage("type.default");
        }
        return switch (type) {
            case IMPORT -> messageBundle.getMessage("type.import");
            case EXPORT -> messageBundle.getMessage("type.export");
            case TRANSFER -> messageBundle.getMessage("type.transfer");
            case ADJUSTMENT -> messageBundle.getMessage("type.adjustment");
            default -> messageBundle.getMessage("type.default");
        };
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String formatDate(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof LocalDate date) {
            return date.format(DATE_FORMAT);
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }
        if (value instanceof ZonedDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMAT);
        }
        return value.toString();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof ZonedDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.atZone(ZoneId.systemDefault()).toInstant();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        return Instant.EPOCH;
    }

    private record PendingApprovalRow(
            String entityId,
            String documentNo,
            String typeLabel,
            String dateLabel,
            String statusLabel,
            String route,
            Instant sortDate
    ) {
    }
}
