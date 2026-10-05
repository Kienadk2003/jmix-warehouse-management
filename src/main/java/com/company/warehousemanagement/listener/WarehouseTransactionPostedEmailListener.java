package com.company.warehousemanagement.listener;

import com.company.warehousemanagement.event.WarehouseTransactionPostedEvent;
import com.company.warehousemanagement.service.WarehouseTransactionEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WarehouseTransactionPostedEmailListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    WarehouseTransactionPostedEmailListener.class
            );

    private final WarehouseTransactionEmailService emailService;

    public WarehouseTransactionPostedEmailListener(
            WarehouseTransactionEmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onTransactionPosted(
            WarehouseTransactionPostedEvent event) {

        try {
            emailService.sendPostedNotification(
                    event.transactionId()
            );
        } catch (Exception exception) {
            log.error(
                    "Không thể gửi email cho phiếu {}",
                    event.transactionId(),
                    exception
            );
        }
    }
}