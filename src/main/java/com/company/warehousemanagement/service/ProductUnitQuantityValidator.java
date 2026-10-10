package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Product;
import com.company.warehousemanagement.entity.Unit;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Enforces the current inventory model: all quantities are stored in the
 * product's base unit. Unit conversion is not supported by the current model.
 */
public final class ProductUnitQuantityValidator {

    private static final int COLUMN_MAX_SCALE = 3;
    private static final int COLUMN_MAX_INTEGER_DIGITS = 16;

    private ProductUnitQuantityValidator() {
    }

    public static void validate(
            DataManager dataManager,
            Product productReference,
            Unit selectedUnit,
            BigDecimal quantity,
            Integer lineNo) {

        String prefix = lineNo == null || lineNo <= 0
                ? ""
                : "Dòng " + lineNo + ": ";

        if (productReference == null || productReference.getId() == null) {
            throw new WarehouseBusinessException(prefix + "sản phẩm không hợp lệ");
        }

        UUID productId = productReference.getId();
        Product product = dataManager.load(Product.class)
                .id(productId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("baseUnit", FetchPlan.BASE))
                .optional()
                .orElseThrow(() -> new WarehouseBusinessException(
                        prefix + "không tìm thấy sản phẩm"));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new WarehouseBusinessException(prefix + "sản phẩm đang ngừng hoạt động");
        }

        Unit baseUnit = product.getBaseUnit();
        if (baseUnit == null || baseUnit.getId() == null) {
            throw new WarehouseBusinessException(
                    prefix + "sản phẩm chưa được cấu hình đơn vị cơ sở");
        }
        if (!Boolean.TRUE.equals(baseUnit.getActive())) {
            throw new WarehouseBusinessException(
                    prefix + "đơn vị cơ sở của sản phẩm đang ngừng hoạt động");
        }

        if (selectedUnit == null || selectedUnit.getId() == null) {
            throw new WarehouseBusinessException(prefix + "chưa chọn đơn vị tính");
        }
        if (!baseUnit.getId().equals(selectedUnit.getId())) {
            throw new WarehouseBusinessException(
                    prefix + "chỉ được sử dụng đơn vị cơ sở của sản phẩm ("
                            + baseUnit.getName() + "); hệ thống hiện chưa hỗ trợ quy đổi đơn vị");
        }

        if (quantity == null || quantity.signum() <= 0) {
            throw new WarehouseBusinessException(prefix + "số lượng phải lớn hơn 0");
        }

        BigDecimal normalized = quantity.stripTrailingZeros();
        int actualScale = Math.max(normalized.scale(), 0);
        if (actualScale > COLUMN_MAX_SCALE) {
            throw new WarehouseBusinessException(
                    prefix + "số lượng chỉ được tối đa " + COLUMN_MAX_SCALE
                            + " chữ số thập phân theo cấu trúc dữ liệu");
        }

        Integer unitScale = baseUnit.getDecimalScale();
        if (unitScale == null || unitScale < 0) {
            throw new WarehouseBusinessException(
                    prefix + "đơn vị cơ sở có cấu hình Decimal Scale không hợp lệ");
        }
        if (actualScale > unitScale) {
            throw new WarehouseBusinessException(
                    prefix + "đơn vị " + baseUnit.getName()
                            + " chỉ cho phép tối đa " + unitScale + " chữ số thập phân");
        }

        int integerDigits = normalized.precision() - normalized.scale();
        if (integerDigits > COLUMN_MAX_INTEGER_DIGITS) {
            throw new WarehouseBusinessException(
                    prefix + "phần nguyên của số lượng không được vượt quá "
                            + COLUMN_MAX_INTEGER_DIGITS + " chữ số");
        }
    }
}
