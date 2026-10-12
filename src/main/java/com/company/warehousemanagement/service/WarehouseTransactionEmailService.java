package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.Partner;
import com.company.warehousemanagement.entity.Warehouse;
import com.company.warehousemanagement.entity.WarehouseTransaction;
import com.company.warehousemanagement.entity.WarehouseTransactionStatus;
import com.company.warehousemanagement.entity.WarehouseTransactionType;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.FetchMode;
import io.jmix.email.EmailInfo;
import io.jmix.email.EmailInfoBuilder;
import io.jmix.email.EmailException;
import io.jmix.email.Emailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WarehouseTransactionEmailService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseTransactionEmailService.class);

    private final DataManager dataManager;
    private final Emailer emailer;
    private final Environment environment;

    public WarehouseTransactionEmailService(DataManager dataManager, Emailer emailer,
                                              Environment environment) {
        this.dataManager = dataManager;
        this.emailer = emailer;
        this.environment = environment;
    }

    /**
     * Called after posting has committed so an email failure never rolls back valid stock data.
     */
    public EmailNotificationResult sendPostedNotification(UUID transactionId) {
        WarehouseTransaction transaction = dataManager.load(WarehouseTransaction.class)
                .id(transactionId)
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("partner", FetchPlan.BASE)
                        .add("sourceWarehouse", FetchPlan.BASE, FetchMode.BATCH)
                        .add("destinationWarehouse", FetchPlan.BASE, FetchMode.BATCH))
                .optional()
                .orElse(null);

        if (transaction == null || transaction.getStatus() != WarehouseTransactionStatus.POSTED
                || (transaction.getType() != WarehouseTransactionType.IMPORT
                && transaction.getType() != WarehouseTransactionType.EXPORT)) {
            return EmailNotificationResult.notSent("Không gửi email: phiếu không hợp lệ hoặc chưa được ghi sổ.");
        }

        Partner partner = transaction.getPartner();
        if (partner == null) {
            return EmailNotificationResult.notSent("Phiếu đã ghi sổ nhưng chưa gửi email vì chưa có đối tác.");
        }

        String recipient = partner.getEmail() == null ? "" : partner.getEmail().trim();
        if (recipient.isEmpty()) {
            return EmailNotificationResult.notSent(
                    "Phiếu đã ghi sổ nhưng chưa gửi email vì đối tác chưa có địa chỉ email.");
        }

        String configurationProblem = validateMailConfiguration();
        if (configurationProblem != null) {
            return EmailNotificationResult.notSent(configurationProblem);
        }

        EmailInfo emailInfo = EmailInfoBuilder
                .create(recipient, createSubject(transaction), createBody(transaction, partner))
                .setBodyContentType("text/plain; charset=UTF-8")
                .build();

        try {
            emailer.sendEmail(emailInfo);
            return EmailNotificationResult.sent(recipient);
        } catch (EmailException exception) {
            log.error("Không thể gửi email cho phiếu {} đến {}",
                    transaction.getDocumentNo(), recipient, exception);
            return EmailNotificationResult.notSent(toUserMessage(exception));
        }
    }

    private String validateMailConfiguration() {
        String host = environment.getProperty("spring.mail.host", "").trim();
        if (host.isEmpty() || "test.host".equalsIgnoreCase(host)) {
            return "Phiếu đã ghi sổ nhưng chưa gửi email vì chưa cấu hình máy chủ SMTP "
                    + "(MAIL_HOST).";
        }

        String fromAddress = environment.getProperty("jmix.email.from-address", "").trim();
        if (fromAddress.isEmpty()) {
            return "Phiếu đã ghi sổ nhưng chưa gửi email vì chưa cấu hình địa chỉ người gửi "
                    + "(MAIL_FROM).";
        }
        return null;
    }

    private String toUserMessage(Throwable failure) {
        StringBuilder details = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                details.append(' ').append(cause.getMessage().toLowerCase());
            }
        }
        String message = details.toString();
        if (message.contains("authenticat") || message.contains("535")
                || message.contains("credential") || message.contains("username and password")) {
            return "Phiếu đã ghi sổ nhưng SMTP từ chối đăng nhập. "
                    + "Vui lòng kiểm tra MAIL_USERNAME và MAIL_PASSWORD.";
        }
        if (message.contains("unknown host") || message.contains("name or service not known")) {
            return "Phiếu đã ghi sổ nhưng không tìm thấy máy chủ SMTP. "
                    + "Vui lòng kiểm tra MAIL_HOST.";
        }
        if (message.contains("connect") || message.contains("timed out")
                || message.contains("timeout")) {
            return "Phiếu đã ghi sổ nhưng không thể kết nối máy chủ SMTP. "
                    + "Vui lòng kiểm tra host, cổng và kết nối mạng.";
        }
        return "Phiếu đã ghi sổ nhưng máy chủ SMTP không gửi được email. "
                + "Chi tiết kỹ thuật đã được ghi vào log ứng dụng.";
    }

    private String createSubject(WarehouseTransaction transaction) {
        String operation = transaction.getType() == WarehouseTransactionType.IMPORT
                ? "nhập kho" : "xuất kho";
        return "Thông báo " + operation + " - " + transaction.getDocumentNo();
    }

    private String createBody(WarehouseTransaction transaction, Partner partner) {
        boolean inbound = transaction.getType() == WarehouseTransactionType.IMPORT;
        Warehouse warehouse = inbound
                ? transaction.getDestinationWarehouse()
                : transaction.getSourceWarehouse();
        String warehouseName = warehouse == null ? "Không xác định" : warehouse.getName();
        String operation = inbound ? "nhập kho" : "xuất kho";
        String warehouseLabel = inbound ? "Kho nhận" : "Kho xuất";

        return """
                Kính gửi %s,

                Phiếu %s %s đã được ghi nhận thành công.

                Ngày chứng từ: %s
                %s: %s
                Trạng thái: POSTED

                Trân trọng.
                """.formatted(
                partner.getName(),
                operation,
                transaction.getDocumentNo(),
                transaction.getDocumentDate(),
                warehouseLabel,
                warehouseName
        );
    }

    public record EmailNotificationResult(boolean sent, String message) {
        public static EmailNotificationResult sent(String recipient) {
            return new EmailNotificationResult(true, "Đã gửi email thông báo đến " + recipient + ".");
        }

        public static EmailNotificationResult notSent(String message) {
            return new EmailNotificationResult(false, message);
        }
    }
}
