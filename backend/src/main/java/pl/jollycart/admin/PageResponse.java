package pl.jollycart.admin;

import java.util.List;

/** Jedna strona wynikow (odpowiednik PageResponse opartego o Page ze Spring Data). */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / size);
        return new PageResponse<>(items, page, size, totalItems, totalPages);
    }
}
