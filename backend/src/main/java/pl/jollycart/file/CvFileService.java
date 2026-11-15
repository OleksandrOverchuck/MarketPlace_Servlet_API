package pl.jollycart.file;

import jakarta.servlet.http.Part;

/** Zapis CV kandydata - dozwolony wylacznie PDF do 5 MB (odpowiednik CvFileService). */
public final class CvFileService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final FileService fileService;
    private final StoredFileDao storedFileDao;

    public CvFileService(FileService fileService, StoredFileDao storedFileDao) {
        this.fileService = fileService;
        this.storedFileDao = storedFileDao;
    }

    public StoredFile storeCv(Part file) {
        validatePdf(file);

        String storedFileName = fileService.storeFile(file);

        return storedFileDao.insert(
                file.getSubmittedFileName(), storedFileName, "application/pdf", file.getSize());
    }

    private void validatePdf(Part file) {
        if (file == null || file.getSize() == 0) {
            throw new IllegalArgumentException("Plik CV nie może być pusty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("CV nie może być większe niż 5 MB");
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("CV musi być zapisane w formacie PDF");
        }

        String fileName = file.getSubmittedFileName();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("CV musi mieć rozszerzenie .pdf");
        }
    }
}
