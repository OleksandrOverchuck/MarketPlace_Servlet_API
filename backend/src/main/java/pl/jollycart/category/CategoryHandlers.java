package pl.jollycart.category;

import java.io.IOException;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/categories (odpowiednik CategoryController). */
public final class CategoryHandlers {

    private final CategoryService categoryService;

    public CategoryHandlers(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public void routes(Router router) {
        router.get("/api/categories", this::getAll);
    }

    private void getAll(Ctx ctx) throws IOException {
        ctx.json(200, categoryService.getAllCategories());
    }
}
