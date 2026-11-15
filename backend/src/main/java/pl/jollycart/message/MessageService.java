package pl.jollycart.message;

import java.nio.file.Path;
import java.util.List;

import jakarta.servlet.http.Part;

import pl.jollycart.db.Db;
import pl.jollycart.file.ChatAttachmentValidator;
import pl.jollycart.file.FileService;
import pl.jollycart.file.StoredFile;
import pl.jollycart.file.StoredFileDao;
import pl.jollycart.notification.NotificationService;
import pl.jollycart.user.User;

/** Wiadomosci i zalaczniki w rozmowach (odpowiednik MessageService). */
public final class MessageService {

    private static final int MAX_CONTENT_LENGTH = 5000;

    private final MessageDao messageDao;
    private final ConversationDao conversationDao;
    private final StoredFileDao storedFileDao;
    private final NotificationService notificationService;
    private final FileService fileService;
    private final ChatAttachmentValidator attachmentValidator;

    public MessageService(
            MessageDao messageDao,
            ConversationDao conversationDao,
            StoredFileDao storedFileDao,
            NotificationService notificationService,
            FileService fileService,
            ChatAttachmentValidator attachmentValidator
    ) {
        this.messageDao = messageDao;
        this.conversationDao = conversationDao;
        this.storedFileDao = storedFileDao;
        this.notificationService = notificationService;
        this.fileService = fileService;
        this.attachmentValidator = attachmentValidator;
    }

    /** Zalacznik gotowy do wyslania: metadane + sciezka pliku na dysku. */
    public record AttachmentDownload(StoredFile file, Path path) {
    }

    public MessageResponse createMessage(long conversationId, User currentUser, CreateMessageRequest request) {
        return Db.inTransaction(() -> {
            requireConversation(conversationId);
            requireParticipant(conversationId, currentUser);

            Message message = messageDao.insert(conversationId, currentUser.id(), request.content(), null);

            // powiadomienie (i mail) dla drugiego uczestnika
            notificationService.createMessageNotifications(message, currentUser, null);

            return MessageResponse.of(message, currentUser, null);
        });
    }

    /*
     * Wiadomosc z zalacznikiem (do 5 MB). Tekst jest opcjonalny - dziala jak podpis pod plikiem.
     */
    public MessageResponse createAttachmentMessage(long conversationId, User currentUser,
                                                   Part file, String content) {
        requireConversation(conversationId);
        requireParticipant(conversationId, currentUser);

        String caption = content == null ? "" : content.trim();
        if (caption.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Wiadomość może mieć maksymalnie 5000 znaków");
        }

        ChatAttachmentValidator.ValidatedAttachment validated = attachmentValidator.validate(file);
        String storedFileName = fileService.storeFile(file);

        try {
            return Db.inTransaction(() -> {
                StoredFile storedFile = storedFileDao.insert(
                        validated.fileName(), storedFileName, validated.contentType(), file.getSize());

                Message message = messageDao.insert(
                        conversationId, currentUser.id(), caption, storedFile.id());

                notificationService.createMessageNotifications(message, currentUser, storedFile);

                return MessageResponse.of(message, currentUser, storedFile);
            });
        } catch (RuntimeException e) {
            // zeby po nieudanym zapisie nie zostawal plik-sierota
            fileService.deleteFile(storedFileName);
            throw e;
        }
    }

    /** Pobranie zalacznika - tylko dla uczestnikow rozmowy. */
    public AttachmentDownload getAttachment(long conversationId, long messageId, User currentUser) {
        requireParticipant(conversationId, currentUser);

        Message message = messageDao.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Wiadomość nie została znaleziona"));

        if (!message.conversationId().equals(conversationId)) {
            throw new IllegalArgumentException("Wiadomość nie należy do tej rozmowy");
        }

        if (message.attachmentId() == null) {
            throw new IllegalArgumentException("Ta wiadomość nie ma załącznika");
        }

        StoredFile attachment = storedFileDao.findById(message.attachmentId())
                .orElseThrow(() -> new IllegalArgumentException("Ta wiadomość nie ma załącznika"));

        return new AttachmentDownload(attachment, fileService.loadFile(attachment.storedFileName()));
    }

    public List<MessageResponse> getConversationMessages(long conversationId, User currentUser) {
        requireParticipant(conversationId, currentUser);

        if (!conversationDao.existsById(conversationId)) {
            throw new IllegalArgumentException("Rozmowa nie została znaleziona");
        }

        return messageDao.findResponsesByConversationId(conversationId);
    }

    /* ------------------------------------------------------------------ */

    private void requireConversation(long conversationId) {
        if (!conversationDao.existsById(conversationId)) {
            throw new IllegalArgumentException("Rozmowa nie została znaleziona");
        }
    }

    private void requireParticipant(long conversationId, User user) {
        if (!conversationDao.isParticipant(conversationId, user.id())) {
            throw new IllegalArgumentException("Nie masz dostępu do tej rozmowy");
        }
    }
}
