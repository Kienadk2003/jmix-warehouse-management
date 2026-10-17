# V17 – Thông báo website (chuông) + Hộp thư email (phong bì)

Dựa trên V16. Header: `[🔔] [✉] [EN/VI] [Avatar] [Tên]` (icon dùng Vaadin Icons: BELL_O, ENVELOPE_O).

## Hai chức năng độc lập
| | Thông báo website | Hộp thư email |
|---|---|---|
| Nguồn | bảng `APP_NOTIFICATION` (DB) | IMAP thật (`ImapEmailInboxService`) |
| Badge | đếm DB theo user đăng nhập | `getUnreadMessageCount()` của IMAP, cache 60s, làm mới nền |
| Service | `AppNotificationService` | `EmailInboxService` |
| Panel | `NotificationPanel` | `EmailInboxPanel` |

`UnreadCountService` cấp số cho hai badge từ hai nguồn riêng.

## Thông báo được tạo khi nào
`WarehouseNotificationListener` nghe `EntityChangedEvent` SAU commit nên không làm hỏng nghiệp vụ kho:
- Phiếu nhập/xuất/chuyển/điều chỉnh hoặc phiếu kiểm kê -> **chờ duyệt**: gửi cho user có role `warehouse-manager` và được gán kho của phiếu (UserWarehouse).
- -> **đã duyệt / từ chối**: gửi cho người tạo phiếu (kèm lý do nếu từ chối).
- Người thao tác không nhận thông báo của chính mình.
Không lưu chuỗi đã dịch: chỉ lưu loại + tham số, hiển thị theo ngôn ngữ EN/VI hiện tại.

## Bảo mật
- Mọi truy vấn lọc theo `CurrentAuthentication` ở server; id của người khác xử lý như không tồn tại.
- Hộp thư email dùng quyền `warehouse.emailInbox.view` (đã cấp cho Warehouse Manager; Admin có `*`).
- Nội dung email chỉ hiển thị dạng văn bản, không render HTML.

## Cấu hình email (biến môi trường, không đưa vào Git)
```
INBOX_IMAP_HOST=imap.example.com
INBOX_IMAP_PORT=993            # mặc định 993
INBOX_IMAP_SSL=true            # mặc định true
INBOX_IMAP_STARTTLS=false      # dùng khi SSL=false
INBOX_IMAP_USERNAME=...        # mặc định lấy MAIL_USERNAME
INBOX_IMAP_PASSWORD=...        # mặc định lấy MAIL_PASSWORD (Gmail: App Password)
INBOX_IMAP_FOLDER=INBOX
```
Thiếu host/user/password => panel hiện "Chưa cấu hình email", không hiển thị thư.

## File
Mới: `entity/AppNotification`, `entity/NotificationType`, `service/AppNotificationService`,
`service/WarehouseNotificationListener`, `service/UnreadCountService`, `email/*` (8 file),
`ui/header/*` (6 file), liquibase `2026-10-15-app-notification.xml`, `header-notifications.css`,
test `notification/AppNotificationServiceTest`, `email/EmailInboxNotConfiguredTest`.
Sửa: `MainView` (+3 dòng), `WarehousePermissions`, `WarehouseManagerRole`, `build.gradle`
(+spring-boot-starter-mail), `application.properties`, `messages_en/vi_VN`, `aura/styles.css` (+1 import).

## Chưa làm / chưa kiểm chứng
- CHƯA build và CHƯA chạy: môi trường tạo file này không có Gradle và không có mạng. Chỉ kiểm tra cú pháp Java, XML Liquibase. Hãy chạy `gradlew.bat compileJava test` và kiểm tra bằng mắt.
- Chưa thử với hộp thư IMAP thật.
- Chưa có thông báo "tồn kho thấp" (dự án chưa có trường ngưỡng cảnh báo) và "thay đổi quyền/phân công kho".
- Chưa có màn hình "Xem tất cả" riêng; panel dùng nút "Tải thêm". Nhãn `common.viewAll` đã có sẵn trong bundle.
- Theme Lumo không được thêm CSS (ứng dụng đang dùng Aura).
