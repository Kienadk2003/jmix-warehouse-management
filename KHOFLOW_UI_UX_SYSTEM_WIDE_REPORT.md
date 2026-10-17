# KhoFlow – Phạm vi UI/UX toàn hệ thống (bản cập nhật)

## Phạm vi trong gói này
- Áp dụng lớp CSS thống nhất cho các view dùng `warehouse-page-layout`, `warehouse-toolbar`, `warehouse-data-grid`, `warehouse-form-layout`, `warehouse-status-bar`, `warehouse-detail-actions`, `warehouse-empty-card` và `warehouse-pagination-panel`.
- Chuẩn hóa độ rộng/đệm ô tìm kiếm và loại bỏ khoảng đệm bên trái dành cho icon tìm kiếm cũ để tránh chữ bị lệch/chồng.
- Chuẩn hóa thẻ form, trường nhập liệu, thanh trạng thái, nút thao tác cố định ở cuối trang, bảng và phân trang trên cả theme Aura và Lumo.
- Bổ sung bản dịch `Unit.decimalScale` và trạng thái rỗng danh sách chuyển kho ở VI/EN.
- Giữ nguyên XML menu hiện có; kiểm tra cho thấy các mục menu được khai báo đều đã có thuộc tính icon.
- Không thay đổi service, quy trình duyệt, phân quyền hoặc mô hình dữ liệu.

## Kiểm tra đã thực hiện
- Kiểm tra cú pháp XML: tất cả XML trong `src/main` đều parse được.
- Kiểm tra `TransferListView.java`: không còn tham chiếu `Target.DATA_LOADER`.
- Chưa xác nhận được `compileJava`: Gradle Wrapper cần tải Gradle 9.5.1 từ `services.gradle.org`, nhưng môi trường hiện tại không có kết nối DNS ra ngoài.

## Giới hạn cần nói rõ
Đây là lớp chuẩn hóa giao diện dùng chung và một số bản dịch bổ sung; không thể khẳng định mọi chuỗi hard-coded trên mọi màn hình đã được chuyển sang message bundle, hoặc chức năng đổi locale đã được kiểm thử end-to-end. Cần chạy ứng dụng và rà soát từng màn hình để phát hiện các nhãn hard-coded còn sót lại. Không coi đây là xác nhận hoàn tất toàn bộ yêu cầu ngôn ngữ.


## v7 follow-up: transfer detail toolbar overlap
- Added explicit normal-flow sizing, spacing, and stacking order for the transfer item toolbar (`itemsButtons`).
- Set the transfer items grid to a lower stacking level so it should not paint over the action buttons.
- No business logic or security rules were changed.
- Build and browser verification are still required; this patch has not been validated in the user's Windows runtime.


## v8 follow-up: menu icon coverage
- Updated icons for Warehouse group, warehouse list, inventory, Units, warehouse operations, import/export, stock card and inventory report to use simpler Vaadin icon identifiers.
- Menu routes, titles, authorization, and business logic were not changed.
- Icon rendering still needs confirmation in the user runtime because this environment cannot launch the Jmix app.
