package pl.jollycart.dispute;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.Part;

import pl.jollycart.db.Db;
import pl.jollycart.dispute.DisputeDtos.DisputeDetail;
import pl.jollycart.dispute.DisputeDtos.DisputeImageInfo;
import pl.jollycart.dispute.DisputeDtos.DisputeSummary;
import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.payment.OrderDao;
import pl.jollycart.payment.OrderResponse;
import pl.jollycart.payment.OrderService;
import pl.jollycart.payment.OrderStatus;
import pl.jollycart.payment.PurchaseOrder;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.StatusException;

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
    static final int MAX_NOTE = 500;

    private final OrderDisputeDao disputeDao;
    private final DisputeImageDao imageDao;
    private final OrderDao orderDao;
    private final UserDao userDao;
    private final OrderService orderService;
    private final FileService fileService;
    private final ImageFileValidator imageFileValidator;

    public DisputeService(
            OrderDisputeDao disputeDao,
            DisputeImageDao imageDao,
            OrderDao orderDao,
            UserDao userDao,
            OrderService orderService,
            FileService fileService,
            ImageFileValidator imageFileValidator
    ) {
        this.disputeDao = disputeDao;
        this.imageDao = imageDao;
        this.orderDao = orderDao;
        this.userDao = userDao;
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

    // =====================================================================
    //  ADMIN / MODERATOR: lista, podglad, decyzja
    // =====================================================================

    public List<DisputeSummary> list(DisputeStatus status) {

        DisputeStatus safeStatus = status == null ? DisputeStatus.OPEN : status;

        return Db.inTransaction(() -> {
            List<OrderDispute> rows;

            if (safeStatus == DisputeStatus.OPEN) {
                backfillLegacyDisputes();
                rows = disputeDao.findByStatusOrderByCreatedAtAsc(DisputeStatus.OPEN);
            } else {
                rows = disputeDao.findTop100ResolvedByResolvedAtDesc();
            }

            return rows.stream().map(this::toSummary).toList();
        });
    }

    public long countOpen() {
        return Db.inTransaction(() -> {
            backfillLegacyDisputes();

            return disputeDao.countByStatus(DisputeStatus.OPEN);
        });
    }

    public DisputeDetail getDetail(long id) {

        OrderDispute dispute = findDispute(id);
        PurchaseOrder order = findOrder(dispute.getOrderId());

        List<DisputeImageInfo> images = imageDao.findByDisputeId(dispute.getId())
                .stream()
                .map(i -> new DisputeImageInfo(i.id(), i.fileName(), i.contentType(), i.fileSize()))
                .toList();

        return new DisputeDetail(
                dispute.getId(),
                order.getId(),
                order.getOfferId(),
                order.getOfferTitle(),
                dispute.getTitle(),
                dispute.getDescription(),
                dispute.getStatus(),
                dispute.getResolution(),
                dispute.getResolverNote(),
                dispute.getResolverNickname(),
                dispute.getCreatedAt(),
                dispute.getResolvedAt(),
                order.getBuyerId(),
                nickname(order.getBuyerId()),
                order.getSellerId(),
                nickname(order.getSellerId()),
                order.getStatus(),
                order.getTotalAmount(),
                order.getSellerPayout(),
                order.getCurrency(),
                images
        );
    }

    public DisputeImage getImage(long disputeId, long imageId) {
        return imageDao.findByIdAndDisputeId(imageId, disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Zdjęcie nie zostało znalezione"));
    }

    /** Sciezka do pliku zdjecia na dysku (IllegalArgumentException, gdy pliku nie ma). */
    public Path imagePath(DisputeImage image) {
        return fileService.loadFile(image.storedFileName());
    }

    public DisputeDetail resolve(User staff, long disputeId, DisputeResolution resolution, String note) {

        if (resolution == null) {
            throw new IllegalArgumentException("Wybierz decyzję");
        }

        String cleanNote = note == null ? "" : note.trim();

        if (cleanNote.length() > MAX_NOTE) {
            throw new IllegalArgumentException("Uzasadnienie może mieć najwyżej " + MAX_NOTE + " znaków");
        }

        return Db.inTransaction(() -> {

            OrderDispute dispute = disputeDao.findByIdForUpdate(disputeId)
                    .orElseThrow(() -> new IllegalArgumentException("Zgłoszenie nie zostało znalezione"));

            if (dispute.getStatus() != DisputeStatus.OPEN) {
                throw StatusException.conflict("To zgłoszenie zostało już rozwiązane");
            }

            PurchaseOrder order = findOrder(dispute.getOrderId());

            // Nie rozstrzygamy sporu, w ktorym ktos jest strona (np. moderator
            // kupil cos od siebie z drugiego konta albo od znajomego).
            if (staff.id().equals(order.getBuyerId()) || staff.id().equals(order.getSellerId())) {
                throw StatusException.conflict(
                        "Nie możesz rozstrzygnąć sporu, w którym jesteś stroną zamówienia");
            }

            // Ruch pieniedzy (Stripe). Wyjatek cofa cala transakcje,
            // wiec zgloszenie zostaje "Aktualne".
            orderService.resolveDispute(order.getId(), resolution == DisputeResolution.REFUND_BUYER);

            dispute.setStatus(DisputeStatus.RESOLVED);
            dispute.setResolution(resolution);
            dispute.setResolverId(staff.id());
            dispute.setResolverNickname(staff.nickname());
            dispute.setResolverNote(cleanNote.isEmpty() ? null : cleanNote);
            dispute.setResolvedAt(LocalDateTime.now());

            disputeDao.updateResolution(dispute);

            // Obie strony widza nowosc w "Zamowieniach" (czerwona kropka).
            PurchaseOrder refreshed = findOrder(order.getId());
            refreshed.setBuyerUnread(true);
            refreshed.setSellerUnread(true);
            orderDao.update(refreshed);

            return getDetail(disputeId);
        });
    }

    // =====================================================================
    //  POMOCNICZE
    // =====================================================================

    /*
     * Zamowienia zgloszone, zanim pojawily sie zgloszenia z tytulem i zdjeciami,
     * maja tylko status DISPUTED i krotki powod. Dokladamy dla nich zgloszenie,
     * zeby nie zginely z panelu.
     */
    private void backfillLegacyDisputes() {

        for (PurchaseOrder order : orderDao.findByStatus(OrderStatus.DISPUTED)) {

            if (disputeDao.existsByOrderId(order.getId())) {
                continue;
            }

            OrderDispute dispute = new OrderDispute();

            String reason = order.getDisputeReason();

            dispute.setOrderId(order.getId());
            dispute.setReporterId(order.getBuyerId());
            dispute.setTitle("Zgłoszenie problemu");
            dispute.setDescription(reason == null || reason.isBlank() ? "(brak opisu)" : reason);
            dispute.setStatus(DisputeStatus.OPEN);
            dispute.setCreatedAt(order.getUpdatedAt());

            disputeDao.insert(dispute);
        }
    }

    private DisputeSummary toSummary(OrderDispute d) {

        PurchaseOrder order = findOrder(d.getOrderId());

        return new DisputeSummary(
                d.getId(),
                order.getId(),
                order.getOfferId(),
                order.getOfferTitle(),
                d.getTitle(),
                d.getStatus(),
                d.getResolution(),
                nickname(order.getBuyerId()),
                nickname(order.getSellerId()),
                (int) imageDao.countByDisputeId(d.getId()),
                d.getCreatedAt(),
                d.getResolvedAt(),
                d.getResolverNickname()
        );
    }

    private OrderDispute findDispute(long id) {
        return disputeDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zgłoszenie nie zostało znalezione"));
    }

    private PurchaseOrder findOrder(long orderId) {
        return orderDao.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Zamówienie nie zostało znalezione"));
    }

    private String nickname(Long userId) {
        return userDao.findById(userId)
                .map(User::nickname)
                .orElse("(usunięty użytkownik)");
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
