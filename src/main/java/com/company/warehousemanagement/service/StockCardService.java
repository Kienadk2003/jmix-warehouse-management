package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.InventoryMovement;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class StockCardService {

    private final DataManager dataManager;

    public StockCardService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public StockCardResult buildStockCard(
            UUID warehouseId,
            UUID productId,
            LocalDate fromDate,
            LocalDate toDate) {

        validateInput(
                warehouseId,
                productId,
                fromDate,
                toDate
        );

        ZoneId zoneId = ZoneId.systemDefault();

        OffsetDateTime startAt =
                fromDate
                        .atStartOfDay(zoneId)
                        .toOffsetDateTime();

        OffsetDateTime endExclusive =
                toDate
                        .plusDays(1)
                        .atStartOfDay(zoneId)
                        .toOffsetDateTime();

        /*
         * =========================
         * OPENING STOCK
         * =========================
         *
         * Tổng movement POSTED
         * trước ngày bắt đầu kỳ.
         */
        BigDecimal openingStock =
                dataManager.loadValue(
                                """
                                select coalesce(sum(e.signedQuantity), 0)
                                from InventoryMovement e
                                where e.warehouse.id = :warehouseId
                                  and e.product.id = :productId
                                  and e.transaction.status = 'POSTED'
                                  and e.occurredAt < :startAt
                                """,
                                BigDecimal.class
                        )
                        .parameter("warehouseId", warehouseId)
                        .parameter("productId", productId)
                        .parameter("startAt", startAt)
                        .one();
        if (openingStock == null) {
            openingStock = BigDecimal.ZERO;
        }

        /*
         * =========================
         * MOVEMENTS IN PERIOD
         * =========================
         *
         * Chỉ lấy transaction POSTED.
         *
         * Sorting xác định:
         * 1. occurredAt
         * 2. transaction.id
         * 3. sequenceNo
         * 4. movement.id
         */
        List<InventoryMovement> movements =
                dataManager
                        .load(InventoryMovement.class)
                        .query(
                                """
                                select e
                                from InventoryMovement e
                                where e.warehouse.id = ?1
                                  and e.product.id = ?2
                                  and e.transaction.status = 'POSTED'
                                  and e.occurredAt >= ?3
                                  and e.occurredAt < ?4
                                order by e.occurredAt asc,
                                         e.transaction.id asc,
                                         e.sequenceNo asc,
                                         e.id asc
                                """,
                                warehouseId,
                                productId,
                                startAt,
                                endExclusive
                        )
                        .fetchPlanProperties(
                                "id",
                                "occurredAt",
                                "signedQuantity",
                                "movementType",
                                "sequenceNo",
                                "transaction.documentNo",
                                "transaction.type"
                        )
                        .list();

        /*
         * =========================
         * RUNNING BALANCE
         * =========================
         */

        BigDecimal runningBalance = openingStock;

        java.util.ArrayList<StockCardRow> rows =
                new java.util.ArrayList<>();

        for (InventoryMovement movement : movements) {

            BigDecimal signedQuantity =
                    movement.getSignedQuantity() == null
                            ? BigDecimal.ZERO
                            : movement.getSignedQuantity();

            runningBalance =
                    runningBalance.add(signedQuantity);

            rows.add(
                    new StockCardRow(
                            movement.getId(),
                            movement.getOccurredAt(),
                            movement.getTransaction()
                                    .getDocumentNo(),
                            movement.getTransaction()
                                    .getType() != null
                                    ? movement.getTransaction()
                                    .getType().getId()
                                    : "-",
                            movement.getMovementType() != null
                                    ? movement.getMovementType().getId()
                                    : "-",
                            signedQuantity,
                            runningBalance
                    )
            );
        }

        /*
         * Nếu kỳ không có movement,
         * closing = opening.
         */
        BigDecimal closingStock = runningBalance;

        /*
         * Current Inventory
         *
         * Đây là số dư hiện tại,
         * để người dùng đối chiếu.
         */
        BigDecimal currentInventory =
                getCurrentInventory(
                        warehouseId,
                        productId
                );

        return new StockCardResult(
                openingStock,
                closingStock,
                currentInventory,
                rows
        );
    }

    private BigDecimal getCurrentInventory(
            UUID warehouseId,
            UUID productId) {

        List<Inventory> inventories =
                dataManager
                        .load(Inventory.class)
                        .query(
                                """
                                select e
                                from Inventory e
                                where e.warehouse.id = ?1
                                  and e.product.id = ?2
                                """,
                                warehouseId,
                                productId
                        )
                        .list();

        if (inventories.isEmpty()) {
            return BigDecimal.ZERO;
        }

        if (inventories.size() > 1) {
            throw new IllegalStateException(
                    "Dữ liệu Inventory không hợp lệ: "
                            + "một Warehouse/Product có nhiều dòng"
            );
        }

        BigDecimal quantity =
                inventories.get(0).getQuantity();

        return quantity != null
                ? quantity
                : BigDecimal.ZERO;
    }

    private void validateInput(
            UUID warehouseId,
            UUID productId,
            LocalDate fromDate,
            LocalDate toDate) {

        if (warehouseId == null) {
            throw new IllegalArgumentException(
                    "Warehouse không được để trống"
            );
        }

        if (productId == null) {
            throw new IllegalArgumentException(
                    "Product không được để trống"
            );
        }

        if (fromDate == null) {
            throw new IllegalArgumentException(
                    "From Date không được để trống"
            );
        }

        if (toDate == null) {
            throw new IllegalArgumentException(
                    "To Date không được để trống"
            );
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "From Date không được lớn hơn To Date"
            );
        }
    }

    public record StockCardRow(
            UUID movementId,
            OffsetDateTime occurredAt,
            String documentNo,
            String transactionType,
            String movementType,
            BigDecimal signedQuantity,
            BigDecimal runningBalance
    ) {
    }

    public record StockCardResult(
            BigDecimal openingStock,
            BigDecimal closingStock,
            BigDecimal currentInventory,
            List<StockCardRow> rows
    ) {
    }
}