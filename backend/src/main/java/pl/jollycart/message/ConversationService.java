package pl.jollycart.message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.message.ConversationResponse.ParticipantResponse;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;

public final class ConversationService {

    private final ConversationDao conversationDao;
    private final UserDao userDao;

    public ConversationService(ConversationDao conversationDao, UserDao userDao) {
        this.conversationDao = conversationDao;
        this.userDao = userDao;
    }

    /** Zwraca istniejaca rozmowe tych dwoch osob o danym ogloszeniu albo tworzy nowa. */
    public ConversationResponse createConversation(User currentUser, long otherUserId, long offerId) {
        return Db.inTransaction(() -> {
            User otherUser = userDao.findById(otherUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Drugi użytkownik nie został znaleziony"));

            if (currentUser.id().equals(otherUser.id())) {
                throw new IllegalArgumentException("Nie możesz utworzyć rozmowy z samym sobą");
            }

            Optional<Conversation> existing =
                    conversationDao.findBetweenUsersAndOffer(currentUser.id(), otherUser.id(), offerId);

            if (existing.isPresent()) {
                Conversation conversation = existing.get();
                return ConversationResponse.of(conversation, participantsOf(conversation.id()));
            }

            LocalDateTime now = LocalDateTime.now();
            long id = conversationDao.insert(offerId, now);
            conversationDao.insertParticipant(id, currentUser.id(), now);
            conversationDao.insertParticipant(id, otherUser.id(), now);

            return ConversationResponse.of(
                    new Conversation(id, offerId, now, now),
                    List.of(ParticipantResponse.of(currentUser), ParticipantResponse.of(otherUser)));
        });
    }

    public List<ConversationResponse> getUserConversations(User currentUser) {
        List<Conversation> conversations = conversationDao.findByParticipantUserId(currentUser.id());

        Map<Long, List<ParticipantResponse>> participants = conversationDao.findParticipants(
                conversations.stream().map(Conversation::id).toList());

        return conversations.stream()
                .map(conversation -> ConversationResponse.of(
                        conversation, participants.getOrDefault(conversation.id(), List.of())))
                .toList();
    }

    public ConversationResponse getConversation(long conversationId, User currentUser) {
        if (!conversationDao.isParticipant(conversationId, currentUser.id())) {
            throw new IllegalArgumentException("Nie masz dostępu do tej rozmowy");
        }

        Conversation conversation = conversationDao.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Rozmowa nie została znaleziona"));

        return ConversationResponse.of(conversation, participantsOf(conversationId));
    }

    private List<ParticipantResponse> participantsOf(long conversationId) {
        return conversationDao.findParticipants(List.of(conversationId))
                .getOrDefault(conversationId, List.of());
    }
}
