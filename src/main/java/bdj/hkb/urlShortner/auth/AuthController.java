package bdj.hkb.urlShortner.auth;

import bdj.hkb.urlShortner.auth.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthClientService authClientService;

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(
            @Valid @RequestBody UserSignupRequest request) {
        MessageResponse response = authClientService.signup(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody UserLoginRequest request) {
        AuthResponse response = authClientService.login(request);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authClientService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        authClientService.logout(authHeader);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?>resendVarification(
            @Valid @RequestBody ResendVerificationRequest request){
        var response = authClientService.resendVerificationEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse>forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request){
        MessageResponse response = authClientService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

}
