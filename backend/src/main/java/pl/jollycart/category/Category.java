package pl.jollycart.category;

/** Wiersz tabeli categories. Serializowany do JSON jako {"id":..., "name":...}. */
public record Category(Long id, String name) {
}
