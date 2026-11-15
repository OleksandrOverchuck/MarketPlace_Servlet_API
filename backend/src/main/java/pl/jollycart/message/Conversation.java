package pl.jollycart.message;

import java.time.LocalDateTime;

/** Wiersz tabeli conversations. Uczestnicy sa w osobnej tabeli (conversation_participants). */
public record Conversation(Long id, Long offerId, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
