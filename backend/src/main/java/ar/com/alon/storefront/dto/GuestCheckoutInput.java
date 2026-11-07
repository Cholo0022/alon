package ar.com.alon.storefront.dto;

import ar.com.alon.storefront.ShopService.Line;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record GuestCheckoutInput(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> lines, @NotNull @Valid Buyer buyer) {
    public record Buyer(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Pattern(regexp = "[0-9]{10}") String phone,
            @NotBlank @Pattern(regexp = "[0-9]{1,12}") String dni,
            @NotBlank @Size(max = 100) String province,
            @NotBlank @Size(max = 100) String city) {}
}
