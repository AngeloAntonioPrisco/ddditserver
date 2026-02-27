package it.unisa.ddditserver.service;

import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepository;
import it.unisa.ddditserver.validators.auth.user.UserValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class UserValidatorImplTest {

    private GremlinAuthRepository gremlinAuthRepository;
    private UserValidatorImpl userValidator;

    @BeforeEach
    void setUp() {
        // Creiamo un mock del repository per non dover connettere il DB
        gremlinAuthRepository = Mockito.mock(GremlinAuthRepository.class);
        userValidator = new UserValidatorImpl(gremlinAuthRepository);
    }

    @Test
    void testIsValidUsername_Success() {
        assertTrue(userValidator.isValidUsername("angelo_antonio"));
    }

    @Test
    void testIsValidUsername_TooShort() {
        assertFalse(userValidator.isValidUsername("an"));
    }

    @Test
    void testIsValidPassword_WeakPassword() {
        // Questa password fallisce perché manca il carattere speciale/numero
        assertFalse(userValidator.isValidPassword("password"));
    }

    @Test
    void testIsValidPassword_Success() {
        // Password forte: 1 Upper, 1 Lower, 1 Digit, 1 Special
        assertTrue(userValidator.isValidPassword("Password123!"));
    }
}