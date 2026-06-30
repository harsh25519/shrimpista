package bdj.hkb.urlShortner.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GithubLoginDto (
        @NotBlank String code,
        @NotNull UUID clientId,
        @NotBlank String clientSecret
){
}
