package com.shelf.assets.internal;

import com.shelf.assets.Assets;
import com.shelf.common.ApiFault;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
class DocumentValidator {
    Validated prepare(Path source, Path photoOutput, Assets.Kind kind) throws IOException {
        if (kind == Assets.Kind.PHOTO) {
            return new Validated(photoOutput, normalizePhoto(source, photoOutput));
        }

        try (var pdf = Loader.loadPDF(source.toFile())) {
            if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 100) {
                throw ApiFault.badRequest("Use an unencrypted PDF with 1–100 pages.");
            }
            var catalog = pdf.getDocumentCatalog().getCOSObject();
            var names = catalog.getCOSDictionary(COSName.NAMES);
            if (catalog.containsKey(COSName.OPEN_ACTION) || catalog.containsKey(COSName.AA)
                    || names != null && (names.containsKey(COSName.JAVA_SCRIPT) || names.containsKey(COSName.EMBEDDED_FILES))) {
                throw ApiFault.badRequest("Export the document as a plain PDF without actions or attachments.");
            }
        }
        return new Validated(source, "application/pdf");
    }

    private String normalizePhoto(Path source, Path destination) throws IOException {
        try (var input = ImageIO.createImageInputStream(source.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ApiFault.badRequest("Use a valid JPEG or PNG photo.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                var format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "png").contains(format) || (long) reader.getWidth(0) * reader.getHeight(0) > 25_000_000) {
                    throw ApiFault.badRequest("Use a JPEG or PNG photo under 25 megapixels.");
                }
                var image = reader.read(0);
                try {
                    if (!ImageIO.write(image, format, destination.toFile())) {
                        throw ApiFault.badRequest("This photo could not be prepared.");
                    }
                } finally {
                    image.flush();
                }
                return "image/" + format;
            } finally {
                reader.dispose();
            }
        }
    }

    record Validated(Path file, String mediaType) {}
}
