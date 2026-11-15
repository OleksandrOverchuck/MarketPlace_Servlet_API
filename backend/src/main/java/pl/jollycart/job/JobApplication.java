package pl.jollycart.job;

/** Wiersz tabeli job_applications w zakresie potrzebnym do sprawdzen (wlasciciel, CV). */
public record JobApplication(Long id, Long offerId, Long userId, Long cvFileId) {
}
