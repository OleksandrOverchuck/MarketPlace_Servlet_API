package pl.jollycart.admin;

import jakarta.validation.constraints.NotNull;

import pl.jollycart.user.Role;

public record ChangeRoleRequest(
        @NotNull(message = "Rola jest wymagana")
        Role role
) {
}
