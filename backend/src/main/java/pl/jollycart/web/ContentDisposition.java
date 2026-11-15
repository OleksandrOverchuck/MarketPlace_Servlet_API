package pl.jollycart.web;

import java.nio.charset.StandardCharsets;

/** Budowanie naglowka Content-Disposition z poprawnie zakodowana nazwa pliku (RFC 6266 / 5987). */
public final class ContentDisposition {

    private ContentDisposition() {
    }

    /**
     * @param inline true = pokaz w przegladarce, false = pobierz jako plik
     */
    public static String of(boolean inline, String filename) {
        String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]|[\"\\\\]", "_");

        return (inline ? "inline" : "attachment")
                + "; filename=\"" + asciiFallback + "\""
                + "; filename*=UTF-8''" + encodeRfc5987(filename);
    }

    /** Kodowanie procentowe bajtow UTF-8; bez kodowania zostaja tylko znaki dozwolone w RFC 5987. */
    private static String encodeRfc5987(String value) {
        StringBuilder sb = new StringBuilder();

        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0;

            if (allowed) {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }

        return sb.toString();
    }
}
