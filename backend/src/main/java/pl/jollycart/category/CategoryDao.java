package pl.jollycart.category;

import java.util.List;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli categories (odpowiednik CategoryRepository). */
public final class CategoryDao {

    private static final RowMapper<Category> MAPPER =
            rs -> new Category(rs.getLong("id"), rs.getString("name"));

    public List<Category> findAllOrderByName() {
        return Db.list("SELECT id, name FROM categories ORDER BY name ASC", MAPPER);
    }

    public boolean existsByName(String name) {
        return Db.exists("SELECT 1 FROM categories WHERE name = ? LIMIT 1", name);
    }

    public void insert(String name) {
        Db.insert("INSERT INTO categories (name) VALUES (?)", name);
    }
}
