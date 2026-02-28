package it.unisa.ddditserver.validators.auth.user;

import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepository;
import it.unisa.ddditserver.subsystems.auth.exceptions.*;
import it.unisa.ddditserver.validators.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Component responsible for validating user credentials and checking username existence.
 *
 * This validator ensures that usernames and passwords meet predefined patterns and length constraints.
 * It also checks the existence of a username using {@link GremlinAuthRepository} and verifies password matching.
 *
 * <ul>
 *     <li>Username must be 3-30 characters long, containing only letters, digits, and underscores.</li>
 *     <li>Password must be 8-50 characters long, including at least one uppercase letter, one lowercase letter,
 *         one digit, and one special character.</li>
 *     <li>Existence of the user in the graph database.</li>
 *     <li>Matching of provided password with the one stored in the graph database.</li>
 * </ul>
 *
 * @author Angelo Antonio Prisco
 * @version 1.3
 * @since 2025-08-13
 */
@Component
public class UserValidatorImpl implements UserValidator {
    private final GremlinAuthRepository gremlinService;

    @Autowired
    public UserValidatorImpl(GremlinAuthRepository gremlinService) {
        this.gremlinService = gremlinService;
    }

    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 30;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^\\w+$");

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 50;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    public boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) return false;
        int length = username.length();
        return length >= USERNAME_MIN_LENGTH &&
                length <= USERNAME_MAX_LENGTH &&
                USERNAME_PATTERN.matcher(username).matches();
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.isEmpty()) return false;
        int length = password.length();
        return length >= PASSWORD_MIN_LENGTH &&
                length <= PASSWORD_MAX_LENGTH &&
                PASSWORD_PATTERN.matcher(password).matches();
    }

    @Override
    public ValidationResult validateUser(UserValidationDTO userValidationDTO) {
        String username =  userValidationDTO.getUsername();
        String password = userValidationDTO.getPassword();

        if (!isValidUsername(username)) {
            throw new InvalidUsernameException("Username must be 3-30 chars long and can contain letters, digits and _ only");
        }
        if (!isValidPassword(password)) {
            throw new InvalidPasswordException("Password must be 8-50 chars and must contains " +
                    "at least one uppercase letter, " +
                    "one lowercase letter, " +
                    "one digit and " +
                    "one special char");
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validateExistence(UserValidationDTO userValidationDTO, boolean exists) {
        String username =  userValidationDTO.getUsername();

        // Password field is null because it is not necessary to check the existence of a user
        UserDTO userDTO = new UserDTO(username,null);

        if (!isValidUsername(username)) {
            throw new InvalidUsernameException(username + " is not a valid username to check its existence");
        }

        if (exists) {
            if (!gremlinService.existsByUser(userDTO)) {
                throw new UserNotFoundException(username + " does not exist as username");
            }
        }
        else {
            if (gremlinService.existsByUser(userDTO)) {
                throw new ExistingUserException(username + " already exists as username");
            }
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validateMatchingPasswords(UserValidationDTO userValidationDTO) {
        String username = userValidationDTO.getUsername();
        String password = userValidationDTO.getPassword();

        // Password field is null because it is not necessary to check the existence of a user
        UserDTO userDTO = new UserDTO(username,null);

        if (!isValidPassword(password)) {
            throw new InvalidPasswordException("Given password is not well-formed");
        }

        UserDTO retrievedUser = gremlinService.findByUser(userDTO);

        String storedPassword = retrievedUser.getPassword();
        String providedPassword = userValidationDTO.getPassword();

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        if (!passwordEncoder.matches(providedPassword, storedPassword)) {
            throw new PasswordsMismatchException("Given password does not match");
        }

        return ValidationResult.valid();
    }
}