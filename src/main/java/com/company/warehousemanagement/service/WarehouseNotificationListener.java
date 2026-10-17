package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.NotificationType;
import com.company.warehousemanagement.entity.Stocktake;
import com.company.warehousemanagement.entity.StocktakeStatus;
import com.company.warehousemanagement.entity.UserWarehouse;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.security.WarehouseManagerRole;
import io.jmix.core.FetchPlan;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns real workflow transitions into website notifications.
 *
 * <p>It listens to Jmix entity change events AFTER the business transaction commits, so
 * it covers every code path that changes a status (views, import/export/transfer/
 * adjustment services, stocktake service) without editing them, and a notification
 * failure can never roll back stock data.</p>
 *
 * <ul>
 *   <li>PENDING_APPROVAL: warehouse managers assigned to the document's warehouse(s)</li>
 *   <li>APPROVED / REJECTED: the user who created the document</li>
 * </ul>
 * The person who performed the action never receives their own notification.
 */
@Component
public class WarehouseNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(WarehouseNotificationListener.class);

    private final UnconstrainedDataManager dataManager;
    private final AppNotificationService notificationService;

    public WarehouseNotificationListener(UnconstrainedDataManager dataManager,
                                         AppNotificationService notificationService) {
        this.dataManager = dataManager;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onWarehouseTransactionChanged(EntityChangedEvent<WarehouseTransaction> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED
                || !event.getChanges().isChanged("status")) {
            return;
        }
        try {
            handleTransaction((UUID) event.getEntityId().getValue());
        } catch (RuntimeException exception) {
            log.warn("Could not create website notification for a warehouse transaction", exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStocktakeChanged(EntityChangedEvent<Stocktake> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED
                || !event.getChanges().isChanged("status")) {
            return;
        }
        try {
            handleStocktake((UUID) event.getEntityId().getValue());
        } catch (RuntimeException exception) {
            log.warn("Could not create website notification for a stocktake", exception);
        }
    }

    private void handleTransaction(UUID id) {
        WarehouseTransaction transaction = dataManager.load(WarehouseTransaction.class)
                .id(id)
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("sourceWarehouse", FetchPlan.INSTANCE_NAME)
                        .add("destinationWarehouse", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElse(null);
        if (transaction == null || transaction.getStatus() == null || transaction.getType() == null) {
            return;
        }

        WarehouseTransactionStatus status = transaction.getStatus();
        NotificationType type;
        switch (status) {
            case PENDING_APPROVAL -> type = NotificationType.TRANSACTION_SUBMITTED;
            case APPROVED -> type = NotificationType.TRANSACTION_APPROVED;
            case REJECTED -> type = NotificationType.TRANSACTION_REJECTED;
            default -> {
                return;
            }
        }

        String subjectType = transaction.getType().getId();
        String actor = actorOf(transaction.getLastModifiedBy());
        String viewId = viewIdFor(subjectType);
        String route = routeFor(subjectType, id);

        Set<String> recipients;
        if (type == NotificationType.TRANSACTION_SUBMITTED) {
            List<UUID> warehouseIds = new ArrayList<>(2);
            addWarehouseId(warehouseIds, transaction.getSourceWarehouse());
            addWarehouseId(warehouseIds, transaction.getDestinationWarehouse());
            recipients = managersFor(warehouseIds);
        } else {
            recipients = creatorOf(transaction.getCreatedBy());
        }
        recipients.remove(actor);

        String detail = type == NotificationType.TRANSACTION_REJECTED ? transaction.getReason() : null;
        notificationService.createForUsers(recipients, type, subjectType,
                transaction.getDocumentNo(), actor, detail, viewId, route);
    }

    private void handleStocktake(UUID id) {
        Stocktake stocktake = dataManager.load(Stocktake.class)
                .id(id)
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("warehouse", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElse(null);
        if (stocktake == null || stocktake.getStatus() == null) {
            return;
        }

        StocktakeStatus status = stocktake.getStatus();
        NotificationType type;
        switch (status) {
            case PENDING_APPROVAL -> type = NotificationType.STOCKTAKE_SUBMITTED;
            case APPROVED -> type = NotificationType.STOCKTAKE_APPROVED;
            case REJECTED -> type = NotificationType.STOCKTAKE_REJECTED;
            default -> {
                return;
            }
        }

        String actor = actorOf(stocktake.getLastModifiedBy());
        Set<String> recipients;
        if (type == NotificationType.STOCKTAKE_SUBMITTED) {
            List<UUID> warehouseIds = new ArrayList<>(1);
            addWarehouseId(warehouseIds, stocktake.getWarehouse());
            recipients = managersFor(warehouseIds);
        } else {
            recipients = creatorOf(stocktake.getCreatedBy());
        }
        recipients.remove(actor);

        notificationService.createForUsers(recipients, type, "STOCKTAKE",
                stocktake.getDocumentNo(), actor, null, "Stocktake.detail", "stocktakes/" + id);
    }

    /**
     * Users holding the Warehouse Manager role AND assigned to at least one of the
     * given warehouses. This mirrors the row-level scope, so a manager is never told
     * about a document they cannot open.
     */
    private Set<String> managersFor(List<UUID> warehouseIds) {
        Set<String> managers = new LinkedHashSet<>();
        List<RoleAssignmentEntity> assignments = dataManager.load(RoleAssignmentEntity.class)
                .query("select e from sec_RoleAssignmentEntity e where e.roleCode = :code")
                .parameter("code", WarehouseManagerRole.CODE)
                .list();
        for (RoleAssignmentEntity assignment : assignments) {
            if (assignment.getUsername() != null) {
                managers.add(assignment.getUsername());
            }
        }
        if (managers.isEmpty() || warehouseIds.isEmpty()) {
            return managers;
        }

        Set<String> assigned = new LinkedHashSet<>();
        List<UserWarehouse> links = dataManager.load(UserWarehouse.class)
                .query("select uw from UserWarehouse uw where uw.warehouse.id in :ids")
                .parameter("ids", warehouseIds)
                .fetchPlan(builder -> builder.add("user", FetchPlan.INSTANCE_NAME))
                .list();
        for (UserWarehouse link : links) {
            if (link.getUser() != null) {
                assigned.add(link.getUser().getUsername());
            }
        }
        managers.retainAll(assigned);
        return managers;
    }

    private Set<String> creatorOf(String createdBy) {
        Set<String> result = new LinkedHashSet<>();
        if (createdBy != null && !createdBy.isBlank()) {
            result.add(createdBy.trim());
        }
        return result;
    }

    private void addWarehouseId(List<UUID> target, Warehouse warehouse) {
        if (warehouse != null && warehouse.getId() != null) {
            target.add(warehouse.getId());
        }
    }

    private String actorOf(String lastModifiedBy) {
        return lastModifiedBy == null || lastModifiedBy.isBlank() ? "system" : lastModifiedBy.trim();
    }

    static String viewIdFor(String subjectType) {
        return switch (subjectType) {
            case "IMPORT" -> "ImportReceipt.detail";
            case "EXPORT" -> "WarehouseTransaction.exportDetail";
            case "TRANSFER" -> "WarehouseTransaction.transferDetail";
            case "ADJUSTMENT" -> "WarehouseTransaction.adjustmentDetail";
            default -> null;
        };
    }

    static String routeFor(String subjectType, UUID id) {
        return switch (subjectType) {
            case "IMPORT" -> "import-receipts/" + id;
            case "EXPORT" -> "export-issues/" + id;
            case "TRANSFER" -> "transfers/" + id;
            case "ADJUSTMENT" -> "adjustments/" + id;
            default -> null;
        };
    }
}
