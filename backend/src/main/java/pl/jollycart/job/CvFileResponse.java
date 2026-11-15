package pl.jollycart.job;

import java.time.LocalDateTime;

import pl.jollycart.file.StoredFile;

public record CvFileResponse(
        Long id,
        String originalFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {

    public static CvFileResponse from(StoredFile file) {
        return new CvFileResponse(
                file.id(), file.originalFileName(), file.contentType(), file.fileSize(), file.createdAt());
    }
}
