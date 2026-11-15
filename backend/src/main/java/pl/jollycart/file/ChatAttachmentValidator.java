package pl.jollycart.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.Part;

/**
 * Walidacja zalacznikow wysylanych w czacie.
 *
 * Nie ufamy Content-Type wyslanemu przez przegladarke: typ ustalamy na podstawie
 * rozszerzenia z bialej listy i sprawdzamy, czy poczatek pliku (sygnatura) do niego pasuje.
 */
public final class ChatAttachmentValidator {

    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_FILE_NAME_LENGTH = 200;

    private enum Signature {
        PDF, PNG, JPEG, GIF, WEBP, OLE, ZIP, TEXT
    }

    private record AllowedType(String contentType, Signature signature) {
    }

    private static final Map<String, AllowedType> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("pdf", new AllowedType("application/pdf", Signature.PDF)),
            Map.entry("png", new AllowedType("image/png", Signature.PNG)),
            Map.entry("jpg", new AllowedType("image/jpeg", Signature.JPEG)),
            Map.entry("jpeg", new AllowedType("image/jpeg", Signature.JPEG)),
            Map.entry("gif", new AllowedType("image/gif", Signature.GIF)),
            Map.entry("webp", new AllowedType("image/webp", Signature.WEBP)),
            Map.entry("doc", new AllowedType("application/msword", Signature.OLE)),
            Map.entry("xls", new AllowedType("application/vnd.ms-excel", Signature.OLE)),
            Map.entry("ppt", new AllowedType("application/vnd.ms-powerpoint", Signature.OLE)),
            Map.entry("docx", new AllowedType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    Signature.ZIP)),
            Map.entry("xlsx", new AllowedType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    Signature.ZIP)),
            Map.entry("pptx", new AllowedType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    Signature.ZIP)),
            Map.entry("txt", new AllowedType("text/plain", Signature.TEXT)),
            Map.entry("csv", new AllowedType("text/csv", Signature.TEXT))
    );

    /** Oczyszczona nazwa pliku i typ zawartosci ustalony przez serwer. */
    public record ValidatedAttachment(String fileName, String contentType) {
    }

    public ValidatedAttachment validate(Part file) {
        if (file == null || file.getSize() == 0) {
            throw new IllegalArgumentException("Plik nie może być pusty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Plik nie może być większy niż " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
        }

        String fileName = sanitizeFileName(file.getSubmittedFileName());
        String extension = getExtension(fileName);
        AllowedType allowedType = ALLOWED_TYPES.get(extension);

        if (allowedType == null) {
            throw new IllegalArgumentException(
                    "Ten typ pliku nie jest obsługiwany. Dozwolone: "
                            + "PDF, PNG, JPG, GIF, WEBP, DOC(X), XLS(X), PPT(X), TXT, CSV");
        }

        validateSignature(file, allowedType.signature());

        return new ValidatedAttachment(fileName, allowedType.contentType());
    }

    /*
     * Zostawiamy sama nazwe pliku (bez sciezki i znakow sterujacych),
     * z ograniczeniem dlugosci - kolumna w bazie ma 255 znakow.
     */
    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Nazwa pliku jest wymagana");
        }

        String name = originalFileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Nazwa pliku jest wymagana");
        }

        if (name.length() > MAX_FILE_NAME_LENGTH) {
            String extension = getExtension(name);
            String suffix = extension.isEmpty() ? "" : "." + extension;
            name = name.substring(0, MAX_FILE_NAME_LENGTH - suffix.length()) + suffix;
        }

        return name;
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');

        if (lastDot == -1 || lastDot == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private void validateSignature(Part file, Signature signature) {
        try (InputStream inputStream = file.getInputStream()) {
            int bytesToRead = signature == Signature.TEXT ? 8192 : 16;
            byte[] header = inputStream.readNBytes(bytesToRead);

            boolean valid = switch (signature) {
                case PDF -> startsWith(header, '%', 'P', 'D', 'F', '-');
                case PNG -> startsWith(header, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
                case JPEG -> startsWith(header, 0xFF, 0xD8, 0xFF);
                case GIF -> startsWith(header, 'G', 'I', 'F', '8');
                case WEBP -> startsWith(header, 'R', 'I', 'F', 'F')
                        && header.length >= 12
                        && header[8] == 'W'
                        && header[9] == 'E'
                        && header[10] == 'B'
                        && header[11] == 'P';
                case OLE -> startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
                case ZIP -> startsWith(header, 'P', 'K', 0x03, 0x04);
                case TEXT -> isProbablyText(header);
            };

            if (!valid) {
                throw new IllegalArgumentException("Zawartość pliku nie pasuje do jego rozszerzenia");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Nie można sprawdzić pliku", e);
        }
    }

    private boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) {
                return false;
            }
        }

        return true;
    }

    /** Plik tekstowy nie powinien zawierac bajtow zerowych. */
    private boolean isProbablyText(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }
}
