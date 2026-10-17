package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.*;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WarehouseReportService {
    private final DataManager data;
    private final InventoryReportService inventory;
    private final ZoneId zone;

    public WarehouseReportService(DataManager data, InventoryReportService inventory,
            @Value("${warehouse.reporting-zone:Asia/Ho_Chi_Minh}") String zone) {
        this.data = data;
        this.inventory = inventory;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() { return LocalDate.now(zone); }

    @Transactional(readOnly = true)
    public Snapshot build(UUID warehouseId, String productCode, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Chọn khoảng ngày hợp lệ: từ ngày không được sau đến ngày.");
        }
        String code = productCode == null ? "" : productCode.trim();
        UUID productId = null;
        if (!code.isEmpty()) {
            var product = data.load(Product.class).query("select p from Product p where lower(p.code) = ?1", code.toLowerCase(Locale.ROOT)).optional();
            if (product.isEmpty()) return new Snapshot(List.of(), List.of(), List.of(), List.of(), List.of());
            productId = product.get().getId();
        }
        String warehouseClause = warehouseId == null ? "" : " and (source.id = ?3 or destination.id = ?3)";
        List<Object> params = new ArrayList<>(List.of(from, to));
        if (warehouseId != null) params.add(warehouseId);
        List<WarehouseTransaction> transactions = data.load(WarehouseTransaction.class)
                .query("select t from WarehouseTransaction t left join t.sourceWarehouse source left join t.destinationWarehouse destination where t.documentDate >= ?1 and t.documentDate <= ?2" + warehouseClause + " order by t.documentDate desc, t.documentNo", params.toArray())
                .fetchPlan(p -> p.addFetchPlan(FetchPlan.BASE).add("sourceWarehouse", FetchPlan.BASE, io.jmix.core.FetchMode.BATCH).add("destinationWarehouse", FetchPlan.BASE, io.jmix.core.FetchMode.BATCH))
                .list();
        List<TransactionItem> lines = new ArrayList<>();
        // Load as roots so row-level policies on TransactionItem/Product scope are enforced.
        for (int offset = 0; offset < transactions.size(); offset += 500) {
            var ids = transactions.subList(offset, Math.min(offset + 500, transactions.size())).stream().map(WarehouseTransaction::getId).toList();
            var loader = data.load(TransactionItem.class).query("select i from TransactionItem i where i.transaction.id in :ids" +
                    (productId == null ? "" : " and i.product.id = :product"))
                    .parameter("ids", ids)
                    .fetchPlan(p -> p.addFetchPlan(FetchPlan.BASE).add("product", FetchPlan.BASE).add("unit", FetchPlan.BASE)
                            .add("transaction", t -> t.addFetchPlan(FetchPlan.BASE).add("sourceWarehouse", FetchPlan.BASE, io.jmix.core.FetchMode.BATCH).add("destinationWarehouse", FetchPlan.BASE, io.jmix.core.FetchMode.BATCH)));
            if (productId != null) loader.parameter("product", productId);
            lines.addAll(loader.list());
        }
        if (productId != null) {
            Set<UUID> ids = lines.stream().map(i -> i.getTransaction().getId()).collect(Collectors.toSet());
            transactions = transactions.stream().filter(t -> ids.contains(t.getId())).toList();
        }
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        var stocktakeLoader = data.load(Stocktake.class).query("select s from Stocktake s where coalesce(s.snapshotAt, s.createdDate) >= :start and coalesce(s.snapshotAt, s.createdDate) < :end" +
                        (warehouseId == null ? "" : " and s.warehouse.id = :warehouse") + " order by s.snapshotAt desc, s.documentNo")
                .parameter("start", start).parameter("end", end)
                .fetchPlan(p -> p.addFetchPlan(FetchPlan.BASE).add("warehouse", FetchPlan.BASE));
        if (warehouseId != null) stocktakeLoader.parameter("warehouse", warehouseId);
        List<Stocktake> stocktakes = stocktakeLoader.list();
        List<StocktakeItem> counts = new ArrayList<>();
        for (int offset = 0; offset < stocktakes.size(); offset += 500) {
            var ids = stocktakes.subList(offset, Math.min(offset + 500, stocktakes.size())).stream().map(Stocktake::getId).toList();
            var loader = data.load(StocktakeItem.class).query("select i from StocktakeItem i where i.stocktake.id in :ids" +
                    (productId == null ? "" : " and i.product.id = :product"))
                    .parameter("ids", ids)
                    .fetchPlan(p -> p.addFetchPlan(FetchPlan.BASE).add("product", pp -> pp.addFetchPlan(FetchPlan.BASE).add("baseUnit", FetchPlan.BASE))
                            .add("stocktake", st -> st.addFetchPlan(FetchPlan.BASE).add("warehouse", FetchPlan.BASE)));
            if (productId != null) loader.parameter("product", productId);
            counts.addAll(loader.list());
        }
        if (productId != null) {
            Set<UUID> ids = counts.stream().map(i -> i.getStocktake().getId()).collect(Collectors.toSet());
            stocktakes = stocktakes.stream().filter(t -> ids.contains(t.getId())).toList();
        }
        return new Snapshot(transactions, lines, stocktakes, counts, inventory.buildReport(warehouseId, productId, from, to));
    }

    public record Snapshot(List<WarehouseTransaction> transactions, List<TransactionItem> lines,
                           List<Stocktake> stocktakes, List<StocktakeItem> counts,
                           List<InventoryReportService.InventoryReportRow> inventory) {
        public long posted(WarehouseTransactionType type) {
            return transactions.stream().filter(t -> t.getType() == type && t.getStatus() == WarehouseTransactionStatus.POSTED).count();
        }
        public long pending() {
            return transactions.stream().filter(t -> t.getStatus() == WarehouseTransactionStatus.PENDING_APPROVAL).count()
                    + stocktakes.stream().filter(s -> s.getStatus() == StocktakeStatus.PENDING_APPROVAL || s.getStatus() == StocktakeStatus.REVIEWING).count();
        }
    }
}
