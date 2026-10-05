package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.InventoryMovement;
import com.company.warehousemanagement.entity.MovementType;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.Warehouse;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryReportService {

    private final DataManager dataManager;

    public InventoryReportService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public List<InventoryReportRow> buildReport(
            UUID warehouseId,
            UUID productId,
            LocalDate fromDate,
            LocalDate toDate) {

        validateInput(
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
         * Lấy toàn bộ movement POSTED trước
         * thời điểm kết thúc kỳ.
         *
         * Sau đó:
         * - trước startAt => Opening
         * - trong kỳ       => In / Out / Adjustment
         */
        StringBuilder jpql = new StringBuilder("""
                select e
                from InventoryMovement e
                where e.transaction.status = 'POSTED'
                  and e.occurredAt < ?1
                """);

        List<Object> parameters = new ArrayList<>();
        parameters.add(endExclusive);

        if (warehouseId != null) {
            jpql.append(" and e.warehouse.id = ?2 ");
            parameters.add(warehouseId);
        }

        if (productId != null) {
            int index = parameters.size() + 1;

            jpql.append(" and e.product.id = ?")
                    .append(index)
                    .append(" ");

            parameters.add(productId);
        }

        jpql.append("""
        order by e.warehouse.id asc,
                 e.product.id asc,
                 e.occurredAt asc,
                 e.transaction.id asc,
                 e.sequenceNo asc,
                 e.id asc
        """);

        List<InventoryMovement> movements =
                dataManager
                        .load(InventoryMovement.class)
                        .query(
                                jpql.toString(),
                                parameters.toArray()
                        )
                        .fetchPlanProperties(
                                "warehouse",
                                "product",
                                "occurredAt",
                                "signedQuantity",
                                "movementType"
                        )
                        .list();

        Map<ReportKey, MutableReportRow> grouped =
                new LinkedHashMap<>();

        for (InventoryMovement movement : movements) {

            if (movement.getWarehouse() == null
                    || movement.getProduct() == null) {
                continue;
            }

            ReportKey key =
                    new ReportKey(
                            movement.getWarehouse().getId(),
                            movement.getProduct().getId()
                    );

            MutableReportRow row =
                    grouped.computeIfAbsent(
                            key,
                            ignored -> new MutableReportRow(
                                    movement.getWarehouse(),
                                    movement.getProduct()
                            )
                    );

            BigDecimal quantity =
                    movement.getSignedQuantity() == null
                            ? BigDecimal.ZERO
                            : movement.getSignedQuantity();

            OffsetDateTime occurredAt =
                    movement.getOccurredAt();

            /*
             * Opening:
             * movement trước ngày bắt đầu.
             */
            if (occurredAt.isBefore(startAt)) {

                row.opening =
                        row.opening.add(quantity);

                continue;
            }

            /*
             * Movement trong kỳ.
             */
            MovementType movementType =
                    movement.getMovementType();

            if (movementType
                    == MovementType.ADJUSTMENT_IN) {

                row.adjustmentIn =
                        row.adjustmentIn.add(
                                quantity.abs()
                        );

            } else if (movementType
                    == MovementType.ADJUSTMENT_OUT) {

                row.adjustmentOut =
                        row.adjustmentOut.add(
                                quantity.abs()
                        );

            } else if (quantity.signum() > 0) {

                /*
                 * IMPORT
                 * TRANSFER_IN
                 * positive REVERSAL
                 */
                row.inbound =
                        row.inbound.add(quantity);

            } else if (quantity.signum() < 0) {

                /*
                 * EXPORT
                 * TRANSFER_OUT
                 * negative REVERSAL
                 */
                row.outbound =
                        row.outbound.add(quantity.abs());
            }
        }

        /*
         * Chỉ giữ những Warehouse/Product
         * có số liệu trong kỳ hoặc có Opening.
         */
        List<InventoryReportRow> result =
                new ArrayList<>();

        for (MutableReportRow row : grouped.values()) {

            BigDecimal closing =
                    row.opening
                            .add(row.inbound)
                            .subtract(row.outbound)
                            .add(row.adjustmentIn)
                            .subtract(row.adjustmentOut);

            result.add(
                    new InventoryReportRow(
                            row.warehouse,
                            row.product,
                            row.opening,
                            row.inbound,
                            row.outbound,
                            row.adjustmentIn,
                            row.adjustmentOut,
                            closing
                    )
            );
        }

        return result;
    }

    private void validateInput(
            LocalDate fromDate,
            LocalDate toDate) {

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

    private record ReportKey(
            UUID warehouseId,
            UUID productId
    ) {
    }

    private static class MutableReportRow {

        private final Warehouse warehouse;
        private final Product product;

        private BigDecimal opening = BigDecimal.ZERO;
        private BigDecimal inbound = BigDecimal.ZERO;
        private BigDecimal outbound = BigDecimal.ZERO;
        private BigDecimal adjustmentIn = BigDecimal.ZERO;
        private BigDecimal adjustmentOut = BigDecimal.ZERO;

        private MutableReportRow(
                Warehouse warehouse,
                Product product) {

            this.warehouse = warehouse;
            this.product = product;
        }
    }

    public record InventoryReportRow(
            Warehouse warehouse,
            Product product,
            BigDecimal opening,
            BigDecimal inbound,
            BigDecimal outbound,
            BigDecimal adjustmentIn,
            BigDecimal adjustmentOut,
            BigDecimal closing
    ) {
    }
}