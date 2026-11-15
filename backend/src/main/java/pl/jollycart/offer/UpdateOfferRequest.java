package pl.jollycart.offer;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOfferRequest(
        @NotBlank(message = "Tytuł jest wymagany")
        @Size(max = 150, message = "Tytuł może mieć maksymalnie 150 znaków")
        String title,

        @NotBlank(message = "Opis jest wymagany")
        @Size(max = 5000, message = "Opis może mieć maksymalnie 5000 znaków")
        String description,

        @DecimalMin(value = "0.00", inclusive = true, message = "Cena nie może być ujemna")
        BigDecimal price,

        @Size(max = 150, message = "Lokalizacja może mieć maksymalnie 150 znaków")
        String location,

        @NotNull(message = "Typ oferty jest wymagany")
        OfferType type,

        @NotNull(message = "Status oferty jest wymagany")
        OfferStatus status,

        @NotNull(message = "Kategoria jest wymagana")
        Long categoryId,

        // Płatność przez JollyCart (opcjonalna, tylko dla typu SALE)
        Boolean onlinePaymentEnabled,

        // Orientacyjny obszar lokalizacji (opcjonalny) - pokazywany na mapie
        Double latitude,
        Double longitude,

        @Min(value = 0, message = "Nieprawidłowy promień obszaru")
        @Max(value = 100000, message = "Nieprawidłowy promień obszaru")
        Integer locationRadiusMeters,

        // Koszt dostawy (opcjonalny) - tylko przy płatności przez JollyCart
        @DecimalMin(value = "0.00", message = "Koszt dostawy nie może być ujemny")
        @DecimalMax(value = "10000.00", message = "Koszt dostawy jest zbyt wysoki")
        BigDecimal shippingCost
) {
}
