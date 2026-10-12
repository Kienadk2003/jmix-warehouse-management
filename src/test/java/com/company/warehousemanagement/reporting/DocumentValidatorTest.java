package com.company.warehousemanagement.reporting;

import com.company.warehousemanagement.service.DocumentValidator;
import com.company.warehousemanagement.exception.WarehouseBusinessException;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class DocumentValidatorTest {
    private final DocumentValidator validator = new DocumentValidator();
    @Test void acceptsRealImagesAndPdf() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThat(validator.validate("chứng từ.PNG", output.toByteArray())).isEqualTo("image/png");
        assertThat(validator.validate("receipt.pdf", "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
    }
    @Test void rejectsExecutableSpoofedEmptyOversizeAndUnsafeNames() {
        for (String name : new String[]{"x.exe", "../x.pdf", "C:\\x.pdf", "x\r\n.pdf", "x.png", "x.pdf"}) {
            assertThatThrownBy(() -> validator.validate(name, "MZ executable".getBytes())).isInstanceOf(WarehouseBusinessException.class);
        }
        assertThatThrownBy(() -> validator.validate("x.pdf", new byte[0])).isInstanceOf(WarehouseBusinessException.class);
        assertThatThrownBy(() -> validator.validate("x.pdf", new byte[DocumentValidator.MAX_BYTES + 1])).isInstanceOf(WarehouseBusinessException.class);
        assertThatThrownBy(() -> validator.validate("x.pdf", "%PDF-1.7 incomplete".getBytes())).isInstanceOf(WarehouseBusinessException.class);
    }
}
