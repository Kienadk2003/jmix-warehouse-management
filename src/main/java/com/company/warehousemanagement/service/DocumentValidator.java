package com.company.warehousemanagement.service;

import com.company.warehousemanagement.exception.WarehouseBusinessException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class DocumentValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    public String validate(String name, byte[] bytes) {
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/")
                || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) {
            throw new WarehouseBusinessException("Tên file không hợp lệ (tối đa 255 ký tự, không chứa đường dẫn).");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new WarehouseBusinessException("File phải có nội dung và không vượt quá 10 MB.");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && bytes.length >= 8
                && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
                && new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length),
                    StandardCharsets.ISO_8859_1).contains("%%EOF")) {
            return "application/pdf";
        }
        boolean png = lower.endsWith(".png") && bytes.length >= 8
                && bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
        boolean jpeg = (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && bytes.length >= 3
                && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
        if (png || jpeg) {
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                        if (pixels > 0 && pixels <= 25_000_000 && reader.read(0) != null) {
                            return png ? "image/png" : "image/jpeg";
                        }
                    } finally { reader.dispose(); }
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // A matching extension/signature alone is insufficient for an image.
            }
        }
        throw new WarehouseBusinessException("Chỉ nhận PDF, PNG, JPG hợp lệ; ảnh tối đa 25 triệu điểm ảnh.");
    }
}
