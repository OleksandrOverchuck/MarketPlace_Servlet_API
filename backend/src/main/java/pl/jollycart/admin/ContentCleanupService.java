package pl.jollycart.admin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.jollycart.db.Db;
import pl.jollycart.file.FileService;
import pl.jollycart.user.User;

/**
 * Trwale usuwa ogloszenia i konta razem ze WSZYSTKIM, co jest do nich podpiete (klucze obce!):
 * zdjecia, aplikacje o prace, CV, rozmowy, wiadomosci, zalaczniki, powiadomienia, oceny, avatar.
 *
 * Pliki z dysku sa kasowane dopiero PO udanym zatwierdzeniu transakcji - jesli cos sie wysypie,
 * nie zostana rekordy wskazujace na brakujace pliki.
 */
public final class ContentCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ContentCleanupService.class);
    private static final String AVATAR_PREFIX = "/api/users/avatar/";

    private final FileService fileService;

    public ContentCleanupService(FileService fileService) {
        this.fileService = fileService;
    }

    /* ------------------------------ ogloszenie ------------------------------ */

    public void deleteOffer(long offerId) {
        Db.inTransaction(() -> {
            Set<String> files = new LinkedHashSet<>();

            removeOfferDependencies(List.of(offerId), files);
            Db.update("DELETE FROM offers WHERE id = ?", offerId);

            deleteFilesAfterCommit(files);
        });
    }

    /* ------------------------------ uzytkownik ------------------------------ */

    public void deleteUser(User user) {
        long userId = user.id();

        Db.inTransaction(() -> {
            Set<String> files = new LinkedHashSet<>();

            List<Long> offerIds = Db.list(
                    "SELECT id FROM offers WHERE user_id = ?", rs -> rs.getLong(1), userId);
            List<Long> conversationIds = Db.list(
                    "SELECT conversation_id FROM conversation_participants WHERE user_id = ?",
                    rs -> rs.getLong(1), userId);

            // 1) powiadomienia, w ktorych uzytkownik jest nadawca / odbiorca
            Db.update("DELETE FROM notifications WHERE recipient_id = ? OR sender_id = ?", userId, userId);

            // 2) rozmowy uzytkownika: powiadomienia -> wiadomosci -> zalaczniki -> uczestnicy -> rozmowy
            if (!conversationIds.isEmpty()) {
                Object[] ids = conversationIds.toArray();
                String in = placeholders(conversationIds);

                Db.update("DELETE FROM notifications WHERE conversation_id IN (" + in + ")", ids);

                List<Long> attachmentIds = Db.list(
                        "SELECT DISTINCT attachment_id FROM messages WHERE conversation_id IN (" + in
                                + ") AND attachment_id IS NOT NULL",
                        rs -> rs.getLong(1), ids);

                Db.update("DELETE FROM messages WHERE conversation_id IN (" + in + ")", ids);
                deleteStoredFiles(attachmentIds, files);

                Db.update("DELETE FROM conversation_participants WHERE conversation_id IN (" + in + ")", ids);
                Db.update("DELETE FROM conversations WHERE id IN (" + in + ")", ids);
            }

            // 3) aplikacje o prace zlozone PRZEZ uzytkownika (+ ich CV)
            deleteJobApplications("user_id = ?", new Object[]{userId}, files);

            // 4) ogloszenia uzytkownika (zdjecia, aplikacje na nie, CV)
            if (!offerIds.isEmpty()) {
                removeOfferDependencies(offerIds, files);
                Db.update("DELETE FROM offers WHERE id IN (" + placeholders(offerIds) + ")", offerIds.toArray());
            }

            // 5) oceny wystawione i otrzymane (maja klucze obce do users)
            Db.update("DELETE FROM user_ratings WHERE rater_id = ? OR rated_user_id = ?", userId, userId);

            // 6) avatar zapisany lokalnie
            String avatarUrl = user.avatarUrl();
            if (avatarUrl != null && avatarUrl.startsWith(AVATAR_PREFIX)) {
                String stored = avatarUrl.substring(AVATAR_PREFIX.length());
                if (!stored.isBlank()) {
                    files.add(stored);
                }
            }

            // 7) samo konto
            Db.update("DELETE FROM users WHERE id = ?", userId);

            deleteFilesAfterCommit(files);
        });
    }

    /* ------------------------------ pomocnicze ------------------------------ */

    private void removeOfferDependencies(List<Long> offerIds, Set<String> files) {
        Object[] ids = offerIds.toArray();
        String in = placeholders(offerIds);

        // zdjecia
        files.addAll(Db.list(
                "SELECT stored_file_name FROM offer_images WHERE offer_id IN (" + in + ")",
                rs -> rs.getString(1), ids));
        Db.update("DELETE FROM offer_images WHERE offer_id IN (" + in + ")", ids);

        // aplikacje o prace na to ogloszenie (+ CV)
        deleteJobApplications("offer_id IN (" + in + ")", ids, files);
    }

    private void deleteJobApplications(String where, Object[] params, Set<String> files) {
        List<Long> cvIds = Db.list(
                "SELECT DISTINCT cv_file_id FROM job_applications WHERE " + where
                        + " AND cv_file_id IS NOT NULL",
                rs -> rs.getLong(1), params);

        Db.update("DELETE FROM job_applications WHERE " + where, params);
        deleteStoredFiles(cvIds, files);
    }

    private void deleteStoredFiles(List<Long> storedFileIds, Set<String> files) {
        if (storedFileIds.isEmpty()) {
            return;
        }

        Object[] ids = storedFileIds.toArray();
        String in = placeholders(storedFileIds);

        files.addAll(Db.list(
                "SELECT stored_file_name FROM stored_files WHERE id IN (" + in + ")",
                rs -> rs.getString(1), ids));
        Db.update("DELETE FROM stored_files WHERE id IN (" + in + ")", ids);
    }

    private void deleteFilesAfterCommit(Set<String> files) {
        if (files.isEmpty()) {
            return;
        }

        Db.afterCommit(() -> {
            for (String file : files) {
                try {
                    fileService.deleteFile(file);
                } catch (RuntimeException e) {
                    log.warn("Nie udało się usunąć pliku {}", file, e);
                }
            }
        });
    }

    private static String placeholders(Collection<?> values) {
        return String.join(",", Collections.nCopies(values.size(), "?"));
    }
}
