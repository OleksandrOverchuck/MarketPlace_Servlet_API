package pl.jollycart.db;

/** Blad warstwy bazy danych (opakowuje SQLException, zeby nie trzeba bylo go deklarowac). */
public class DbException extends RuntimeException {

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
