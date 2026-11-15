package pl.jollycart.image;

import java.time.LocalDateTime;

public record OfferImageResponse(
        Long id,
        String fileName,
        String storedFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {

    public static OfferImageResponse from(OfferImage image) {
        return new OfferImageResponse(
                image.id(),
                image.fileName(),
                image.storedFileName(),
                image.contentType(),
                image.fileSize(),
                image.createdAt()
        );
    }
}
