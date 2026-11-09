package pl.jollycart.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Zamienia biezacy wiersz ResultSet na obiekt (zastepuje mapowanie ORM). */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
