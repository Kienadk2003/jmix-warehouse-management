package com.company.warehousemanagement.service;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class MasterDataImportDialog {
    private final MasterDataImportService service;
    public MasterDataImportDialog(MasterDataImportService service) { this.service=service; }
    public void open(String type, Runnable onFinished) {
        Dialog dialog=new Dialog(); dialog.setHeaderTitle("Import CSV / Excel — "+type); dialog.setWidth("38rem");
        VerticalLayout layout=new VerticalLayout(); layout.setPadding(false); layout.setSpacing(true);
        layout.add(new Paragraph("File phải có dòng tiêu đề ở hàng đầu. Mã trùng sẽ được xử lý theo lựa chọn bên dưới."));
        ComboBox<MasterDataImportService.DuplicateMode> duplicate=new ComboBox<>("Khi mã đã tồn tại");
        duplicate.setItems(MasterDataImportService.DuplicateMode.values());
        duplicate.setItemLabelGenerator(m -> switch (m) {
            case SKIP -> "Thêm dữ liệu — bỏ qua mã đã tồn tại";
            case UPDATE -> "Thêm dữ liệu — cập nhật mã đã tồn tại";
            case REPLACE_ALL -> "Thay thế toàn bộ dữ liệu danh mục này";
        });
        duplicate.setValue(MasterDataImportService.DuplicateMode.SKIP); duplicate.setWidthFull();
        Checkbox confirmReplace = new Checkbox("Tôi hiểu dữ liệu cũ của danh mục này sẽ bị xóa trước khi nhập file mới.");
        confirmReplace.setVisible(false);
        duplicate.addValueChangeListener(event -> confirmReplace.setVisible(event.getValue() == MasterDataImportService.DuplicateMode.REPLACE_ALL));
        MemoryBuffer buffer=new MemoryBuffer(); Upload upload=new Upload(buffer); upload.setAcceptedFileTypes(".csv",".xlsx"); upload.setMaxFiles(1); upload.setMaxFileSize(15*1024*1024); upload.setDropAllowed(true);
        Div result=new Div(); result.setWidthFull();
        upload.addSucceededListener(event->{
            try {
                if (duplicate.getValue() == MasterDataImportService.DuplicateMode.REPLACE_ALL && !confirmReplace.getValue()) {
                    Notification.show("Hãy xác nhận thao tác thay thế toàn bộ dữ liệu trước khi tải file.", 5000, Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                var rows=service.read(buffer.getInputStream(),event.getFileName());
                var report=service.importRows(type,rows,duplicate.getValue());
                StringBuilder text=new StringBuilder("Đã tạo: "+report.created()+" | Cập nhật: "+report.updated()+" | Bỏ qua: "+report.skipped()+" | Lỗi: "+report.errors().size());
                for(var err:report.errors()) text.append("\nDòng ").append(err.row()).append(": ").append(err.message());
                result.removeAll(); result.add(new Pre(text.toString()));
                if(report.errors().isEmpty()){Notification.show("Import hoàn tất: "+report.created()+" mới, "+report.updated()+" cập nhật, "+report.skipped()+" bỏ qua",5000,Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);onFinished.run();}
                else Notification.show("Import xong nhưng có dòng lỗi. Xem báo cáo trong cửa sổ.",5000,Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_ERROR);
            } catch(Exception ex){ Notification.show("Không thể import: "+(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()),6000,Notification.Position.TOP_END).addThemeVariants(NotificationVariant.LUMO_ERROR); }
        });
        Button close=new Button("Đóng",e->dialog.close()); layout.add(duplicate, confirmReplace, upload, result, close); dialog.add(layout); dialog.open();
    }
}
