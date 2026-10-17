# Chứng từ và báo cáo kho

## Sử dụng

1. Tạo và lưu phiếu nhập, xuất, chuyển hoặc điều chỉnh ở trạng thái **Nháp**.
2. Mở chi tiết phiếu, tìm phần **Chứng từ đính kèm**, chọn hoặc kéo thả nhiều file.
3. Mỗi file được lưu ngay; thông báo riêng cho từng file thành công hoặc lỗi. Các file đã tải thành công vẫn được giữ khi một file khác thất bại.
4. Gửi duyệt sau khi hoàn tất chứng từ. Manager mở phần chứng từ để **Xem** hoặc **Tải xuống** trước khi duyệt. Chứng từ không còn được bổ sung sau khi gửi duyệt.
5. Vào **Báo cáo → Tổng quan & báo cáo kho** (hoặc nút tương ứng ở trang chủ). Chọn từ ngày, đến ngày, kho, mã sản phẩm, rồi bấm **Xem báo cáo**. Khoảng ngày mặc định là toàn bộ tháng hiện tại. Để trống kho/mã sản phẩm để xem toàn bộ dữ liệu được cấp quyền.

Khi phiếu nhập hoặc xuất được ghi sổ, hệ thống gửi email đồng bộ đến email của đối tác. Thông báo trên màn hình xác nhận địa chỉ nhận khi gửi thành công. Nếu đối tác chưa có email hoặc SMTP lỗi, phiếu vẫn được ghi sổ và thông báo sẽ nêu rõ email chưa gửi được.

Để gửi mail thật, cấu hình các biến môi trường `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` và `MAIL_FROM`, sau đó khởi động lại ứng dụng. Với Gmail, dùng `smtp.gmail.com`, cổng `587` và App Password. Máy chủ dùng STARTTLS và SMTP authentication theo cấu hình trong `application.properties`. Có thể tắt từng tùy chọn bằng `SPRING_MAIL_STARTTLS_ENABLE=false` hoặc `SPRING_MAIL_SMTP_AUTH=false` nếu máy chủ nội bộ không yêu cầu.

## Quy ước dữ liệu

- **Từ ngày** và **Đến ngày** cho phép nhập trực tiếp khoảng ngày cần xem; cả hai đầu mốc đều được tính vào báo cáo.
- Mã sản phẩm được khớp chính xác, bỏ khoảng trắng đầu/cuối và không phân biệt chữ hoa/thường. Mã không tồn tại hoặc không được cấp quyền trả về báo cáo rỗng.
- Phiếu nhập/xuất/chuyển/điều chỉnh lọc theo **ngày chứng từ**, bao gồm mọi trạng thái. Thống kê đếm phiếu một lần, kể cả phiếu có nhiều dòng hàng.
- Kiểm kho lọc theo thời điểm chốt số liệu `snapshotAt`; phiếu chưa chốt dùng ngày tạo. Giá trị thực đếm trống hiển thị **Chưa kiểm**, không được coi là 0.
- Tồn đầu kỳ là tổng bút toán trước đầu ngày bắt đầu. Cuối kỳ bao gồm bút toán đến trước 00:00 ngày sau ngày kết thúc. Bao gồm giao dịch đã ghi sổ và giao dịch đã đảo cùng các bút toán đảo tương ứng.
- Vào/ra kho trong báo cáo tồn bao gồm cả chuyển kho và bút toán đảo; điều chỉnh tăng/giảm có cột riêng. Số lượng không cộng gộp giữa các đơn vị khác nhau.
- Dashboard trong báo cáo dùng cùng bộ lọc: phiếu nhập/xuất đã ghi sổ và phiếu chờ duyệt trong khoảng ngày. Trang chủ vẫn hiển thị tổng số phiếu toàn thời gian.
- Múi giờ báo cáo mặc định `Asia/Ho_Chi_Minh`, có thể cấu hình bằng `warehouse.reporting-zone`.

## Chứng từ và quyền truy cập

- Chấp nhận PDF, PNG, JPG/JPEG, tối đa 10 MB/file; ảnh tối đa 25 triệu điểm ảnh. Kiểm tra tên file, phần mở rộng, chữ ký file; ảnh phải giải mã được, PDF phải có header và dấu kết thúc. Đây không phải chức năng quét virus hay xác minh toàn bộ cấu trúc PDF.
- Metadata `TransactionDocument` lưu liên kết `WarehouseTransaction`, tên file, loại nội dung, dung lượng, người/thời điểm tải lên và `FileRef`.
- Nội dung lưu qua Jmix Local File Storage. Cần sao lưu cả cơ sở dữ liệu lẫn thư mục storage của ứng dụng.
- Viewer/Staff/Manager được xem/tải theo quyền `warehouse.document.view` và phạm vi kho trên phiếu. Staff/Manager được thêm file khi có `warehouse.transaction.editDraft` và phiếu còn nháp.
- Phải gán role `warehouse-scope` và phân công `UserWarehouse` cho tài khoản cần giới hạn theo kho. Role `staff-product-scope` giới hạn thêm các dòng dữ liệu theo nhóm sản phẩm.
- Metadata chỉ được ghi qua service sau khi kiểm tra phiếu, không cấp quyền ghi trực tiếp cho các UI role. Nếu giao dịch DB rollback, file vừa lưu được dọn khỏi storage.
- Liquibase tự tạo bảng `TRANSACTION_DOCUMENT` khi khởi động; không cần chạy SQL thủ công.

## Kiểm tra

Chạy `./gradlew.bat test --offline` với JDK và Gradle cache đã cài. Kiểm thử bao gồm xác thực file, ranh giới khoảng ngày, dữ liệu tồn kho, lọc phiếu/kiểm kho, nhiều file, khóa tải lên sau gửi duyệt, quyền xem/tải chéo kho và mở các màn hình mới.
