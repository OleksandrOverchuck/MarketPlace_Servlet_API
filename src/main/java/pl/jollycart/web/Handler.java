package pl.jollycart.web;

/** Obsluga jednego endpointu (odpowiednik metody kontrolera). */
@FunctionalInterface
public interface Handler {
    void handle(Ctx ctx) throws Exception;
}
