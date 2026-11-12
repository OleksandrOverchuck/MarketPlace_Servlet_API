package pl.jollycart.category;

import java.util.List;

import pl.jollycart.db.Db;

public final class CategoryService {

    private final CategoryDao categoryDao;

    public CategoryService(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    public List<Category> getAllCategories() {
        return categoryDao.findAllOrderByName();
    }

    /** Kategorie poczatkowe - tworzone przy starcie, jesli jeszcze ich nie ma. */
    public void createInitialCategories() {
        Db.inTransaction(() -> {
            createIfNotExists("Elektronika");
            createIfNotExists("Motoryzacja");
            createIfNotExists("Dom i ogród");
            createIfNotExists("Moda");
            createIfNotExists("Sport i rekreacja");
            createIfNotExists("Praca");
            createIfNotExists("Usługi");
            createIfNotExists("Nauka i edukacja");
            createIfNotExists("Inne");
        });
    }

    private void createIfNotExists(String name) {
        if (!categoryDao.existsByName(name)) {
            categoryDao.insert(name);
        }
    }
}
