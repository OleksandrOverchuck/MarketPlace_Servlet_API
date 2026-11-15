package pl.jollycart.rating;

import java.time.LocalDateTime;
import java.util.Optional;

import pl.jollycart.db.Db;

/** Zapytania SQL do tabeli user_ratings (odpowiednik UserRatingRepository). */
public final class UserRatingDao {

    /** Wiersz oceny: id (do aktualizacji) i liczba gwiazdek. */
    public record Rating(Long id, int stars) {
    }

    public Optional<Rating> findByRaterAndRated(long raterId, long ratedUserId) {
        return Db.one(
                "SELECT id, stars FROM user_ratings WHERE rater_id = ? AND rated_user_id = ?",
                rs -> new Rating(rs.getLong("id"), rs.getInt("stars")),
                raterId, ratedUserId
        );
    }

    public long countByRatedUser(long ratedUserId) {
        return Db.one("SELECT COUNT(*) FROM user_ratings WHERE rated_user_id = ?",
                rs -> rs.getLong(1), ratedUserId).orElse(0L);
    }

    /** Srednia ocen; 0 gdy uzytkownik nie ma jeszcze ocen (SQL zwraca NULL, getDouble daje 0). */
    public double averageStars(long ratedUserId) {
        return Db.one("SELECT AVG(stars) FROM user_ratings WHERE rated_user_id = ?",
                rs -> rs.getDouble(1), ratedUserId).orElse(0.0);
    }

    public void insert(long raterId, long ratedUserId, int stars) {
        LocalDateTime now = LocalDateTime.now();

        Db.insert(
                "INSERT INTO user_ratings (rater_id, rated_user_id, stars, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                raterId, ratedUserId, stars, now, now
        );
    }

    public void updateStars(long id, int stars) {
        Db.update("UPDATE user_ratings SET stars = ?, updated_at = ? WHERE id = ?",
                stars, LocalDateTime.now(), id);
    }

    public void delete(long raterId, long ratedUserId) {
        Db.update("DELETE FROM user_ratings WHERE rater_id = ? AND rated_user_id = ?",
                raterId, ratedUserId);
    }
}
