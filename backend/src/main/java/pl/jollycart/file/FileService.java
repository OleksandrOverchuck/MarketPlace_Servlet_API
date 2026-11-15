package pl.jollycart.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import jakarta.servlet.http.Part;

/**
 * Zapis, odczyt i usuwanie plikow w katalogu uploads (nazwy zapisanych plikow to UUID + rozszerzenie).
 */
public final class FileService {

    private final Path uploadDirectory;

    public FileService(String uploadDirectory) {
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Nie można utworzyć katalogu na pliki", e);
        }
    }

    /** Zapisuje przeslany plik i zwraca jego nowa (unikalna) nazwe na dysku. */
    public String storeFile(Part file) {
        if (file == null || file.getSize() == 0) {
            throw new IllegalArgumentException("Plik nie może być pusty");
        }

        String originalFileName = file.getSubmittedFileName();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Nazwa pliku jest wymagana");
        }

        String storedFileName = UUID.randomUUID() + getFileExtension(originalFileName);
        Path targetPath = resolveInsideUploadDirectory(storedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się zapisać pliku", e);
        }

        return storedFileName;
    }

    /** Zwraca sciezke do istniejacego, czytelnego pliku. */
    public Path loadFile(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            throw new IllegalArgumentException("Nazwa pliku jest wymagana");
        }

        Path filePath = resolveInsideUploadDirectory(storedFileName);

        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new IllegalArgumentException("Plik nie został znaleziony");
        }

        return filePath;
    }

    public void deleteFile(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }

        Path filePath = resolveInsideUploadDirectory(storedFileName);

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się usunąć pliku", e);
        }
    }

    /** Ochrona przed path traversal (np. "../../haslo.txt"): plik musi lezec bezposrednio w uploads. */
    private Path resolveInsideUploadDirectory(String fileName) {
        Path path = uploadDirectory.resolve(fileName).normalize();

        if (!uploadDirectory.equals(path.getParent())) {
            throw new IllegalArgumentException("Nieprawidłowa ścieżka pliku");
        }

        return path;
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? "" : fileName.substring(lastDot);
    }
}
