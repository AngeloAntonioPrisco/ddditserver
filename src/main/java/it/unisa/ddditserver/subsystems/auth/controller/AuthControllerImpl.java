package it.unisa.ddditserver.subsystems.auth.controller;

import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.*;
import it.unisa.ddditserver.subsystems.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthControllerImpl implements AuthController {

    private static final String DETAILS_KEY = "details";

    private static final String ERROR_KEY = "error";

    private AuthService authService;

    public AuthControllerImpl(AuthService authService) {
        this.authService = authService;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7);
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody UserDTO userDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return authService.signup(userDTO, token);
        } catch (InvalidUsernameException | InvalidPasswordException |
                 ExistingUserException | LoggedUserException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (AuthException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during signup", DETAILS_KEY, e.getMessage()));
        }
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody UserDTO userDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return authService.login(userDTO, token);
        } catch (InvalidUsernameException | InvalidPasswordException |
                 UserNotFoundException | PasswordsMismatchException |
                 LoggedUserException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (AuthException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during login", DETAILS_KEY, e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return authService.logout(token);
        } catch (NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (AuthException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during logout", DETAILS_KEY, e.getMessage()));
        }
    }
}
