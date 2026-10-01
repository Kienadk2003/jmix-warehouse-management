package com.company.warehousemanagement.service;

import com.company.warehousemanagement.dto.CreateImportReceiptCommand;
import com.company.warehousemanagement.entity.*;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ImportReceiptService {

    private final DataManager dataManager;

    public ImportReceiptService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Transactional
    public WarehouseTransaction createDraft(
            CreateImportReceiptCommand command) {

        validateCreateCommand(command);

        Warehouse warehouse =
                loadActiveWarehouse(command.getWarehouseId());

        Partner supplier =
                loadActiveSupplier(command.getPartnerId());

        LocalDate documentDate =
                command.getDocumentDate() != null
                        ? command.getDocumentDate()
                        : LocalDate.now();

        WarehouseTransaction receipt =
                dataManager.create(WarehouseTransaction.class);

        receipt.setDocumentNo(
                generateDocumentNumber(documentDate)
        );

        receipt.setType(
                WarehouseTransactionType.IMPORT
        );

        receipt.setStatus(
                WarehouseTransactionStatus.DRAFT
        );

        receipt.setDocumentDate(documentDate);
        receipt.setDestinationWarehouse(warehouse);
        receipt.setPartner(supplier);
        receipt.setReason(command.getReason());

        return dataManager.save(receipt);
    }

    private void validateCreateCommand(
            CreateImportReceiptCommand command) {

        if (command == null) {
            throw new WarehouseBusinessException(
                    "Dữ liệu tạo phiếu không được để trống"
            );
        }

        if (command.getWarehouseId() == null) {
            throw new WarehouseBusinessException(
                    "Bạn chưa chọn kho nhận"
            );
        }

        if (command.getPartnerId() == null) {
            throw new WarehouseBusinessException(
                    "Bạn chưa chọn nhà cung cấp"
            );
        }
    }

    private Warehouse loadActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = dataManager
                .load(Warehouse.class)
                .id(warehouseId)
                .optional()
                .orElseThrow(() ->
                        new WarehouseBusinessException(
                                "Không tìm thấy kho nhận"
                        )
                );

        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new WarehouseBusinessException(
                    "Kho nhận đang ngừng hoạt động"
            );
        }

        return warehouse;
    }

    private Partner loadActiveSupplier(UUID partnerId) {
        Partner partner = dataManager
                .load(Partner.class)
                .id(partnerId)
                .optional()
                .orElseThrow(() ->
                        new WarehouseBusinessException(
                                "Không tìm thấy đối tác"
                        )
                );

        if (!Boolean.TRUE.equals(partner.getActive())) {
            throw new WarehouseBusinessException(
                    "Đối tác đang ngừng hoạt động"
            );
        }

        PartnerType type = partner.getPartnerType();

        if (type != PartnerType.SUPPLIER
                && type != PartnerType.BOTH) {
            throw new WarehouseBusinessException(
                    "Đối tác không phải nhà cung cấp"
            );
        }

        return partner;
    }

    private String generateDocumentNumber(
            LocalDate documentDate) {

        String datePart = documentDate.format(
                DateTimeFormatter.BASIC_ISO_DATE
        );

        String randomPart = UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();

        return "IMP-" + datePart + "-" + randomPart;
    }
}