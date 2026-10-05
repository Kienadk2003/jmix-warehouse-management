package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.email.EmailInfo;
import io.jmix.email.EmailInfoBuilder;
import io.jmix.email.Emailer;
import io.jmix.email.EmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.util.UUID;

@Service
public class WarehouseTransactionEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    WarehouseTransactionEmailService.class
            );

    private final DataManager dataManager;
    private final Emailer emailer;

    public WarehouseTransactionEmailService(
            DataManager dataManager,
            Emailer emailer) {
        this.dataManager = dataManager;
        this.emailer = emailer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPostedNotification(
            UUID transactionId) throws EmailException {

        WarehouseTransaction transaction = dataManager
                .load(WarehouseTransaction.class)
                .id(transactionId)
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("partner", FetchPlan.BASE)
                        .add("sourceWarehouse", FetchPlan.BASE)
                        .add("destinationWarehouse", FetchPlan.BASE))
                .one();

        Partner partner = transaction.getPartner();

        if (partner == null) {
            log.warn(
                    "Phiếu {} không có đối tác, bỏ qua gửi email",
                    transaction.getDocumentNo()
            );
            return;
        }

        if (partner.getEmail() == null
                || partner.getEmail().isBlank()) {
            log.warn(
                    "Đối tác {} chưa có email, bỏ qua gửi thông báo",
                    partner.getName()
            );
            return;
        }

        String subject = createSubject(transaction);
        String body = createBody(transaction, partner);

        EmailInfo emailInfo = EmailInfoBuilder
                .create(partner.getEmail(), subject, body)
                .setBodyContentType("text/plain; charset=UTF-8")
                .build();

        emailer.sendEmail(emailInfo);
    }

    private String createSubject(
            WarehouseTransaction transaction) {

        if (transaction.getType()
                == WarehouseTransactionType.IMPORT) {
            return "Thông báo nhập kho - "
                    + transaction.getDocumentNo();
        }

        return "Thông báo xuất kho - "
                + transaction.getDocumentNo();
    }

    private String createBody(
            WarehouseTransaction transaction,
            Partner partner) {

        if (transaction.getType()
                == WarehouseTransactionType.IMPORT) {

            Warehouse warehouse =
                    transaction.getDestinationWarehouse();

            String warehouseName = warehouse == null
                    ? "Không xác định"
                    : warehouse.getName();

            return """
                    Kính gửi %s,

                    Phiếu nhập kho %s đã được ghi nhận thành công.

                    Ngày chứng từ: %s
                    Kho nhận: %s
                    Trạng thái: POSTED

                    Trân trọng.
                    """.formatted(
                    partner.getName(),
                    transaction.getDocumentNo(),
                    transaction.getDocumentDate(),
                    warehouseName
            );
        }

        Warehouse warehouse =
                transaction.getSourceWarehouse();

        String warehouseName = warehouse == null
                ? "Không xác định"
                : warehouse.getName();

        return """
                Kính gửi %s,

                Phiếu xuất kho %s đã được ghi nhận thành công.

                Ngày chứng từ: %s
                Kho xuất: %s
                Trạng thái: POSTED

                Trân trọng.
                """.formatted(
                partner.getName(),
                transaction.getDocumentNo(),
                transaction.getDocumentDate(),
                warehouseName
        );
    }
}