package pl.jollycart.message;

import java.time.LocalDateTime;
import java.util.List;

import pl.jollycart.user.User;

/** JSON rozmowy - identyczny z ConversationResponse w wersji Spring Boot. */
public record ConversationResponse(
        Long id,
        Long offerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ParticipantResponse> participants
) {

    public static ConversationResponse of(Conversation conversation, List<ParticipantResponse> participants) {
        return new ConversationResponse(
                conversation.id(),
                conversation.offerId(),
                conversation.createdAt(),
                conversation.updatedAt(),
                participants
        );
    }

    public record ParticipantResponse(Long userId, String nickname, String avatarUrl) {

        public static ParticipantResponse of(User user) {
            return new ParticipantResponse(user.id(), user.nickname(), user.avatarUrl());
        }
    }
}
