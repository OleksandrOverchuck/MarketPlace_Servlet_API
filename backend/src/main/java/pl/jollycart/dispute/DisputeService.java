package pl.jollycart.dispute;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.Part;

import pl.jollycart.db.Db;
import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.payment.OrderResponse;
import pl.jollycart.payment.OrderService;
import pl.jollycart.user.User;

/*
 * Zgloszenia problemow z zamowieniami (port DisputeService ze Spring Boot).
 *
 *  - kupujacy otwiera zgloszenie (tytul + opis + zdjecia) -> zamowienie DISPUTED,
 *  - administrator / moderator widzi je w panelu ("Aktualne") i je rozstrzyga:
 *      REFUND_BUYER  -> zwrot pieniedzy kupujacemu,
 *      PAY_SELLER    -> wyplata srodkow sprzedawcy,
 *    po czym zgloszenie trafia do "Rozwiazane".
 *
 * Same pieniadze obsluguje OrderService (Stripe) - tu tylko dokladamy tresc
 * zgloszenia, zdjecia i historie decyzji.
 */
public final class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    static final int MAX_IMAGES = 5;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final int MAX_TITLE = 150;
    static final int MAX_DESCRIPTION = 2000;

    private final OrderDisputeDao disputeDao;
    private final DisputeImageDao imageDao;
    private final OrderService orderService;
    private final FileService fileService;
    private final ImageFileValidator imageFileValidator;

    public DisputeService(
            OrderDisputeDao disputeDao,
            DisputeImageDao imageDao,
            OrderService orderService,
            FileService fileService,
            ImageFileValidator imageFileValidator
    ) {
        this.disputeDao = disputeDao;
        this.imageDao = imageDao;
        this.orderService = orderService;
        this.fileService = fileService;
        this.imageFileValidator = imageFileValidator;
    }

    // =====================================================================
    //  KUPUJACY: otwarcie zgloszenia
    // =====================================================================

    public OrderResponse openDispute(
            User buyer,
            long orderId,
            String title,
            String description,
            List<Part> files
    ) {

        String cleanTitle = title == null ? "" : title.trim();
        String cleanDescription = description == null ? "" : description.trim();

        if (cleanTitle.isEmpty()) {
            throw new IllegalArgumentException("Podaj tytuł zgłoszenia");
        }

        if (cleanTitle.length() > MAX_TITLE) {
            throw new IllegalArgumentException("Tytuł może mieć najwyżej " + MAX_TITLE + " znaków");
        }

        if (cleanDescription.isEmpty()) {
            throw new IllegalArgumentException("Opisz, jaki jest problem");
        }

        if (cleanDescription.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("Opis może mieć najwyżej " + MAX_DESCRIPTION + " znaków");
        }

        List<Part> images = new ArrayList<>();

        if (files != null) {
            for (Part file : files) {
                // Puste pole pliku w formularzu (nic nie wybrano) pomijamy
                if (file != null && file.getSize() > 0) {
                    images.add(file);
                }
            }
        }

        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Możesz dodać najwyżej " + MAX_IMAGES + " zdjęć");
        }

        // Walidacja zdjec przed jakakolwiek zmiana (typ, rozmiar, sygnatura pliku)
        for (Part image : images) {
            imageFileValidator.validate(image, MAX_IMAGE_BYTES);
        }

        return Db.inTransaction(() -> {

            // Zmiana statusu zamowienia na DISPUTED (sprawdza wlasciciela i termin).
            // W "powodzie" zamowienia zostaje sam tytul zgloszenia.
            OrderResponse response = orderService.openDispute(buyer, orderId, cleanTitle);

            OrderDispute dispute = new OrderDispute();

            dispute.setOrderId(orderId);
            dispute.setReporterId(buyer.id());
            dispute.setTitle(cleanTitle);
            dispute.setDescription(cleanDescription);
            dispute.setStatus(DisputeStatus.OPEN);

            disputeDao.insert(dispute);

            List<String> storedNames = new ArrayList<>();

            try {
                for (Part image : images) {
                    String storedName = fileService.storeFile(image);

                    storedNames.add(storedName);

                    imageDao.insert(
                            dispute.getId(),
                            safeFileName(image.getSubmittedFileName()),
                            storedName,
                            image.getContentType(),
                            image.getSize()
                    );
                }
            } catch (RuntimeException e) {
                // Transakcja zostanie cofnieta - sprzatamy juz zapisane pliki.
                for (String storedName : storedNames) {
                    try {
                        fileService.deleteFile(storedName);
                    } catch (RuntimeException cleanupError) {
                        log.warn("Nie udało się usunąć pliku {} po błędzie zgłoszenia", storedName, cleanupError);
                    }
                }

                throw e;
            }

            return response;
        });
    }

    // Nazwa pliku trafia do bazy (255 znakow) i do naglowka - bez sciezek i znakow sterujacych.
    private String safeFileName(String original) {

        String name = original == null ? "" : original;

        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }

        name = name.replaceAll("[\\p{Cntrl}]", "").trim();

        if (name.isEmpty()) {
            return "zdjecie";
        }

        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
