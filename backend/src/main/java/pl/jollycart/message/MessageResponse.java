package pl.jollycart.message;

import java.time.LocalDateTime;

import pl.jollycart.file.StoredFile;
import pl.jollycart.user.User;

/** JSON wiadomosci - identyczny z MessageResponse w wersji Spring Boot. */
public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderNickname,
        String senderAvatarUrl,
        String content,
        AttachmentResponse attachment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MessageResponse of(Message message, User sender, StoredFile attachment) {
        return new MessageResponse(
                message.id(),
                message.conversationId(),
                sender.id(),
                sender.nickname(),
                sender.avatarUrl(),
                message.content(),
                AttachmentResponse.from(attachment),
                message.createdAt(),
                message.updatedAt()
        );
    }

    /** Metadane zalacznika; sam plik pobierany z GET /api/conversations/{id}/messages/{id}/attachment. */
    public record AttachmentResponse(Long id, String fileName, String contentType, Long fileSize) {

        public static AttachmentResponse from(StoredFile file) {
            if (file == null) {
                return null;
            }
            return new AttachmentResponse(
                    file.id(), file.originalFileName(), file.contentType(), file.fileSize());
        }
    }
}
