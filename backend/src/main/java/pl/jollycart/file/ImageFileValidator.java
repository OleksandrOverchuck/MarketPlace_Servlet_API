package pl.jollycart.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import jakarta.servlet.http.Part;

/** Sprawdza, czy przeslany plik to poprawny obraz PNG lub JPG (typ, rozszerzenie i naglowek pliku). */
public final class ImageFileValidator {

    public void validate(Part file, long maxSize) {
        if (file == null || file.getSize() == 0) {
            throw new IllegalArgumentException("Zdjęcie nie może być puste");
        }

        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    "Zdjęcie nie może być większe niż " + (maxSize / 1024 / 1024) + " MB");
        }

        String contentType = file.getContentType();
        if (!"image/png".equalsIgnoreCase(contentType) && !"image/jpeg".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("Dozwolone są tylko pliki PNG i JPG");
        }

        String fileName = file.getSubmittedFileName();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Nazwa pliku jest wymagana");
        }

        String lowerName = fileName.toLowerCase(Locale.ROOT);
        boolean validExtension = lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg");

        if (!validExtension) {
            throw new IllegalArgumentException("Dozwolone są tylko pliki PNG i JPG");
        }

        validateFileSignature(file);
    }

    /** Pierwsze bajty pliku musza pasowac do PNG albo JPEG - nazwa i typ moga byc sfalszowane. */
    private void validateFileSignature(Part file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(8);

            if (header.length < 8) {
                throw new IllegalArgumentException("Nieprawidłowy plik graficzny");
            }

            boolean png = header[0] == (byte) 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A;

            boolean jpg = header[0] == (byte) 0xFF
                    && header[1] == (byte) 0xD8
                    && header[2] == (byte) 0xFF;

            if (!png && !jpg) {
                throw new IllegalArgumentException("Plik nie jest prawidłowym obrazem PNG lub JPG");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Nie można sprawdzić pliku", e);
        }
    }
}
