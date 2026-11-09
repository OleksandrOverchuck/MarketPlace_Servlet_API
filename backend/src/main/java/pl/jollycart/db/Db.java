package pl.jollycart.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import pl.jollycart.config.AppConfig;

/**
 * Dostep do bazy przez JDBC: pula polaczen (HikariCP), proste metody pomocnicze
 * oraz reczne zarzadzanie transakcjami.
 *
 * Odpowiednik tego, co w Spring Boot robia Spring Data JPA i @Transactional.
 */
public final class Db {

    private static final Logger log = LoggerFactory.getLogger(Db.class);

    private static HikariDataSource dataSource;

    /** Polaczenie biezacej transakcji (jesli jest otwarta) - jedno na watek. */
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    private Db() {
    }

    @FunctionalInterface
    private interface SqlFunction<T, R> {
        R apply(T value) throws SQLException;
    }

    /* ------------------------------------------------------------------
     * Start / stop
     * ------------------------------------------------------------------ */

    public static void init(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUser());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(config.dbPoolSize());
        hikari.setPoolName("jollycart-pool");

        // Hikari od razu otwiera polaczenie - jesli baza jest niedostepna,
        // aplikacja nie wystartuje (tak jak w Spring Boot).
        dataSource = new HikariDataSource(hikari);

        try (Connection connection = dataSource.getConnection()) {
            log.info("Polaczono z baza danych: {} {}",
                    connection.getMetaData().getDatabaseProductName(),
                    connection.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            throw new DbException("Nie udalo sie polaczyc z baza danych", e);
        }
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /* ------------------------------------------------------------------
     * Transakcje
     * ------------------------------------------------------------------ */

    /**
     * Wykonuje prace w jednej transakcji. Wyjatek (dowolny) cofa zmiany.
     * Zagniezdzone wywolanie dolacza sie do istniejacej transakcji
     * (jak propagacja REQUIRED w Springu).
     */
    public static <T> T inTransaction(Supplier<T> work) {
        if (CURRENT.get() != null) {
            return work.get();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            CURRENT.set(connection);

            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (Throwable t) {
                rollbackQuietly(connection);
                throw t;
            } finally {
                CURRENT.remove();
            }
        } catch (SQLException e) {
            throw new DbException("Blad transakcji", e);
        }
    }

    public static void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            log.warn("Nie udalo sie cofnac transakcji", e);
        }
    }

    /* ------------------------------------------------------------------
     * Zapytania
     * ------------------------------------------------------------------ */

    public static <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet rs = statement.executeQuery()) {
                    List<T> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(mapper.map(rs));
                    }
                    return result;
                }
            }
        });
    }

    public static <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next()
                            ? Optional.of(mapper.map(rs))
                            : Optional.<T>empty();
                }
            }
        });
    }

    /** Zwraca true, jesli zapytanie zwroci co najmniej jeden wiersz. */
    public static boolean exists(String sql, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /** INSERT / UPDATE / DELETE - zwraca liczbe zmienionych wierszy. */
    public static int update(String sql, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                return statement.executeUpdate();
            }
        });
    }

    /** INSERT - zwraca wygenerowany klucz glowny (AUTO_INCREMENT). */
    public static long insert(String sql, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, params);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Brak wygenerowanego klucza");
                    }
                    return keys.getLong(1);
                }
            }
        });
    }

    /* ------------------------------------------------------------------
     * Pomocnicze
     * ------------------------------------------------------------------ */

    private static <R> R withConnection(SqlFunction<Connection, R> action) {
        Connection transactional = CURRENT.get();

        try {
            if (transactional != null) {
                return action.apply(transactional);
            }

            try (Connection connection = dataSource.getConnection()) {
                return action.apply(connection);
            }
        } catch (SQLException e) {
            throw new DbException("Blad bazy danych", e);
        }
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            int index = i + 1;

            if (value == null) {
                statement.setNull(index, Types.NULL);
            } else if (value instanceof Enum<?> e) {
                statement.setString(index, e.name());
            } else {
                statement.setObject(index, value);
            }
        }
    }
}
