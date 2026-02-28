package it.unisa.ddditserver.subsystems.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.db.cosmos.auth.CosmosAuthRepository;
import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepository;
import it.unisa.ddditserver.subsystems.auth.exceptions.*;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {
    private GremlinAuthRepository gremlinService;
    private CosmosAuthRepository cosmosAuthRepository;
    private JWTokenValidator jwtTokenValidator;
    private UserValidator userValidator;

    public AuthServiceImpl(GremlinAuthRepository gremlinService, CosmosAuthRepository cosmosAuthRepository, JWTokenValidator jwtTokenValidator, UserValidator userValidator) {
        this.gremlinService = gremlinService;
        this.cosmosAuthRepository = cosmosAuthRepository;
        this.jwtTokenValidator = jwtTokenValidator;
        this.userValidator = userValidator;
    }

    @Value("${JWT_SECRET}")
    public String jwtSecretBase64;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] decodedKey = Base64.getDecoder().decode(jwtSecretBase64);
        this.secretKey = Keys.hmacShaKeyFor(decodedKey);
    }

    private String generateToken(String username) {
        long expirationMillis = 1000 * 60 * 480; // 8 hours
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public ResponseEntity<Map<String, String>> signup(UserDTO userDTO, String token) {
        // Check if the token is valid, if it is valid the user is already logged in
        String retrievedUsername = jwtTokenValidator.isTokenValid(token);

        if (retrievedUsername != null) {
            throw new LoggedUserException(retrievedUsername + " is already logged");
        }

        String username = userDTO.getUsername();
        String password = userDTO.getPassword();

        UserValidationDTO userValidationDTO = new UserValidationDTO(username, password);

        // Check if user's credentials are well-formed
        userValidator.validateUser(userValidationDTO);

        // Check if the username already exists in graph database
        // Check UserValidator interface for more information about the exists flag
        userValidator.validateExistence(userValidationDTO, false);

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode(password);
        userDTO.setPassword(hashedPassword);

        // Save user's information on graph database and generate a JWT token for authentication
        try {
            gremlinService.saveUser(userDTO);
        } catch (Exception e) {
            throw new AuthException(e.getMessage());
        }

        try {
            token = generateToken(username);
        } catch (Exception e) {
            throw new AuthException("Signup failed during token generation");
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "User " + username + " registered successfully");
        response.put("token", token);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, String>> login(UserDTO userDTO, String token) {
        // Check if the token is valid, if it is valid the user is already logged in
        String retrievedUsername = jwtTokenValidator.isTokenValid(token);

        if (retrievedUsername != null) {
            throw new LoggedUserException(retrievedUsername + " is already logged");
        }

        String username = userDTO.getUsername();
        String password = userDTO.getPassword();

        UserValidationDTO userValidationDTO = new UserValidationDTO(username, password);

        // Check if user's credentials are well-formed
        userValidator.validateUser(userValidationDTO);

        // Check if the username already exists in graph database
        // Check UserValidator interface for more information about the exists flag
        userValidator.validateExistence(userValidationDTO, true);

        // Check if the password given by the user match with the password stored in graph database
        userValidator.validateMatchingPasswords(userValidationDTO);

        // Generate a JWT token for authentication
        try {
            token = generateToken(username);
        } catch (Exception e) {
            throw new AuthException("Login failed during token generation");
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "User " + username + " logged in successfully");
        response.put("token", token);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, String>> logout(String token) {
        // Check if the token is valid, if it is invalid the user is not logged
        String retrievedUsername = jwtTokenValidator.isTokenValid(token);

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        try {
            cosmosAuthRepository.blacklistToken(token);
        } catch(Exception e){
            throw new AuthException(e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "User " + retrievedUsername + " logged out successfully, token will be blacklisted");

        return ResponseEntity.ok(response);
    }
}
