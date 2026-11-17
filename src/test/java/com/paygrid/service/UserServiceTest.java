package com.paygrid.service;

import com.paygrid.dto.request.RegisterRequest;
import com.paygrid.dto.response.UserResponse;
import com.paygrid.entity.Role;
import com.paygrid.entity.User;
import com.paygrid.exception.ResourceNotFoundException;
import com.paygrid.exception.UserAlreadyExistsException;
import com.paygrid.repository.UserRepository;
import com.paygrid.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private RegisterRequest registerRequest;
    private User mockUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
                "john_doe",
                "john@example.com",
                "Password123!",
                Role.ROLE_USER,
                new BigDecimal("500.00")
        );

        mockUser = new User(
                "john_doe",
                "john@example.com",
                "$2a$10$encodedPasswordHash",
                Role.ROLE_USER,
                "PG-12345678",
                new BigDecimal("500.00")
        );
        mockUser.setId(1L);
    }

    @Test
    @DisplayName("Should successfully register a new user with hashed password and initial balance")
    void register_Success() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$10$encodedPasswordHash");
        when(userRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        UserResponse response = userService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("john_doe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(response.getRole()).isEqualTo(Role.ROLE_USER);

        verify(passwordEncoder).encode("Password123!");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserAlreadyExistsException when username is duplicate")
    void register_DuplicateUsername_ThrowsException() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Username 'john_doe' is already taken");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw UserAlreadyExistsException when email is duplicate")
    void register_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email 'john@example.com' is already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should return user when finding by existing username")
    void getUserByUsername_Found() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(mockUser));

        User user = userService.getUserByUsername("john_doe");

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when username does not exist")
    void getUserByUsername_NotFound_ThrowsException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByUsername("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with username: unknown");
    }
}

