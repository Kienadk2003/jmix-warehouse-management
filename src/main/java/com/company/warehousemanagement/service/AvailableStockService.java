package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Inventory;
import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import io.jmix.core.DataManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AvailableStockService {

    private final DataManager dataManager;

    public AvailableStockService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /**
     * MVP:
     * Available = On hand = Inventory.quantity
     *
     * reservedQuantity chưa được trừ ở giai đoạn hiện tại.
     */
    public BigDecimal getAvailableStock(
            UUID warehouseId,
            UUID productId) {

        validateIds(warehouseId, productId);

        List<Inventory> inventories = dataManager
                .load(Inventory.class)
                .query("""
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
            throw new WarehouseBusinessException(
                    "Dữ liệu tồn kho không hợp lệ: "
                            + "một kho/sản phẩm có nhiều dòng Inventory"
            );
        }

        BigDecimal quantity = inventories.get(0).getQuantity();

        return quantity != null
                ? quantity
                : BigDecimal.ZERO;
    }

    /**
     * Kiểm tra số lượng yêu cầu có nằm trong available stock hay không.
     */
    public void validateAvailableStock(
            UUID warehouseId,
            UUID productId,
            BigDecimal requestedQuantity) {

        if (requestedQuantity == null
                || requestedQuantity.signum() <= 0) {

            throw new WarehouseBusinessException(
                    "Số lượng yêu cầu phải lớn hơn 0"
            );
        }

        BigDecimal availableQuantity =
                getAvailableStock(
                        warehouseId,
                        productId
                );

        if (availableQuantity.compareTo(requestedQuantity) < 0) {

            throw new WarehouseBusinessException(
                    "Không đủ tồn kho. "
                            + "Yêu cầu: " + requestedQuantity
                            + ", tồn khả dụng: " + availableQuantity
            );
        }
    }

    /**
     * Dùng tại thời điểm POST.
     *
     * Khóa dòng Inventory để tránh hai giao dịch
     * cùng lúc cùng trừ một lượng tồn.
     */
    public Inventory loadAndLockInventory(
            UUID warehouseId,
            UUID productId) {

        validateIds(warehouseId, productId);

        List<Inventory> inventories = dataManager
                .load(Inventory.class)
                .query("""
                        select e
                        from Inventory e
                        where e.warehouse.id = ?1
                          and e.product.id = ?2
                        """,
                        warehouseId,
                        productId
                )
                .lockMode(LockModeType.PESSIMISTIC_WRITE)
                .list();

        if (inventories.isEmpty()) {
            throw new WarehouseBusinessException(
                    "Không có tồn kho cho sản phẩm này tại kho xuất"
            );
        }

        if (inventories.size() > 1) {
            throw new WarehouseBusinessException(
                    "Dữ liệu tồn kho không hợp lệ: "
                            + "một kho/sản phẩm có nhiều dòng Inventory"
            );
        }

        return inventories.get(0);
    }

    /**
     * Kiểm tra tồn trực tiếp trên dòng Inventory đã được lock.
     *
     * Đây là check cuối cùng trước khi POST.
     */
    public void validateLockedInventory(
            Inventory inventory,
            BigDecimal requestedQuantity,
            Product product) {

        if (inventory == null) {
            throw new WarehouseBusinessException(
                    "Không tìm thấy tồn kho"
            );
        }

        if (requestedQuantity == null
                || requestedQuantity.signum() <= 0) {

            throw new WarehouseBusinessException(
                    "Số lượng yêu cầu phải lớn hơn 0"
            );
        }

        BigDecimal availableQuantity =
                inventory.getQuantity();

        if (availableQuantity == null) {
            availableQuantity = BigDecimal.ZERO;
        }

        if (availableQuantity.compareTo(requestedQuantity) < 0) {

            String productName = getProductDisplayName(product);

            throw new WarehouseBusinessException(
                    "Không đủ tồn kho cho sản phẩm '"
                            + productName
                            + "'. "
                            + "Yêu cầu: " + requestedQuantity
                            + ", tồn hiện tại: " + availableQuantity
            );
        }
    }

    private void validateIds(
            UUID warehouseId,
            UUID productId) {

        if (warehouseId == null) {
            throw new WarehouseBusinessException(
                    "Kho không hợp lệ"
            );
        }

        if (productId == null) {
            throw new WarehouseBusinessException(
                    "Sản phẩm không hợp lệ"
            );
        }
    }

    private String getProductDisplayName(Product product) {

        if (product == null) {
            return "không xác định";
        }

        if (product.getCode() != null
                && !product.getCode().isBlank()) {

            return product.getCode();
        }

        if (product.getName() != null
                && !product.getName().isBlank()) {

            return product.getName();
        }

        return "không xác định";
    }
}