package it.unisa.ddditserver.subsystems.auth.controller;

import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

/**
 * Controller interface for authentication endpoints.
 *
 * Provides operations for user signup, login, and logout
 * within the authentication subsystem.
 *
 * @author Angelo Antonio Prisco
 * @version 1.1
 * @since 2025-08-11
 */
public interface AuthController {

    /**
     * Handles the request to sign up a new user.
     *
     * @param userDTO the user data transfer object containing signup information
     * @param request the HTTP servlet request object
     * @return a ResponseEntity indicating the result of the signup operation
     */
    ResponseEntity<Map<String, String>> signup(@RequestBody UserDTO userDTO, HttpServletRequest request);

    /**
     * Handles the request to log in a user.
     *
     * @param userDTO the user data transfer object containing login information
     * @param request the HTTP servlet request object
     * @return a ResponseEntity indicating the result of the login operation
     */
    ResponseEntity<Map<String, String>> login(@RequestBody UserDTO userDTO, HttpServletRequest request);

    /**
     * Handles the request to log out the current user.
     *
     * @param request the HTTP servlet request object
     * @return a ResponseEntity indicating the result of the logout operation
     */
    ResponseEntity<Map<String, String>> logout(HttpServletRequest request);

}
