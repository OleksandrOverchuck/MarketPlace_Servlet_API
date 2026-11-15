package pl.jollycart.image;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.Part;

import pl.jollycart.db.Db;
import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferDao;
import pl.jollycart.user.User;

/** Zdjecia ofert (odpowiednik OfferImageService). */
public final class OfferImageService {

    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    private final OfferImageDao offerImageDao;
    private final OfferDao offerDao;
    private final FileService fileService;
    private final ImageFileValidator imageFileValidator;

    public OfferImageService(
            OfferImageDao offerImageDao,
            OfferDao offerDao,
            FileService fileService,
            ImageFileValidator imageFileValidator
    ) {
        this.offerImageDao = offerImageDao;
        this.offerDao = offerDao;
        this.fileService = fileService;
        this.imageFileValidator = imageFileValidator;
    }

    public OfferImageResponse uploadImage(long offerId, User currentUser, Part file) {
        return Db.inTransaction(() -> {
            Offer offer = offerDao.findById(offerId)
                    .orElseThrow(() -> new IllegalArgumentException("Oferta nie została znaleziona"));

            if (!offer.userId().equals(currentUser.id())) {
                throw new IllegalArgumentException("Nie możesz dodać zdjęcia do cudzej oferty");
            }

            imageFileValidator.validate(file, MAX_IMAGE_SIZE);

            String storedFileName = fileService.storeFile(file);

            OfferImage image = new OfferImage(
                    null, offer.id(), file.getSubmittedFileName(), storedFileName,
                    file.getContentType(), file.getSize(), LocalDateTime.now());

            long id = offerImageDao.insert(image);

            return OfferImageResponse.from(new OfferImage(
                    id, image.offerId(), image.fileName(), image.storedFileName(),
                    image.contentType(), image.fileSize(), image.createdAt()));
        });
    }

    public List<OfferImageResponse> getImagesByOfferId(long offerId) {
        if (!offerDao.existsById(offerId)) {
            throw new IllegalArgumentException("Oferta nie została znaleziona");
        }

        return offerImageDao.findByOfferIdOrderByCreatedAtAsc(offerId).stream()
                .map(OfferImageResponse::from)
                .toList();
    }

    public OfferImage getImageById(long imageId) {
        return offerImageDao.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Zdjęcie nie zostało znalezione"));
    }

    public void deleteImage(long imageId, User currentUser) {
        Db.inTransaction(() -> {
            OfferImage image = getImageById(imageId);

            Offer offer = offerDao.findById(image.offerId())
                    .orElseThrow(() -> new IllegalArgumentException("Oferta nie została znaleziona"));

            if (!offer.userId().equals(currentUser.id())) {
                throw new IllegalArgumentException("Nie możesz usunąć zdjęcia z cudzej oferty");
            }

            fileService.deleteFile(image.storedFileName());
            offerImageDao.delete(image.id());
        });
    }
}
