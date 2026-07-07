package com.pulseflow.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseflow.backend.auth.dto.AuthTokenResponse;
import com.pulseflow.backend.auth.dto.LoginRequest;
import com.pulseflow.backend.auth.dto.RegisterRequest;
import com.pulseflow.backend.auth.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import com.pulseflow.backend.monitoring.AuditLogService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Mockito injects mocks.
    }

    @Test
    void registerCreatesAdminUserAndReturnsProfile() {
        RegisterRequest request = new RegisterRequest("admin", "admin@example.com", "password123");
        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("admin");
        savedUser.setEmail("admin@example.com");
        savedUser.setPassword("encoded-password");
        savedUser.setRole(UserRole.ADMIN);
        savedUser.setCreatedAt(java.time.LocalDateTime.of(2026, 7, 2, 12, 0));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserProfileResponse response = authService.register(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.email()).isEqualTo("admin@example.com");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.createdAt()).isEqualTo(savedUser.getCreatedAt());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("admin", "admin@example.com", "password123")))
                .isInstanceOf(ResponseStatusException.class)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginReturnsJwtForValidCredentials() {
        LoginRequest request = new LoginRequest("admin", "password123");
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setEmail("admin@example.com");
        user.setPassword("encoded-password");
        user.setRole(UserRole.ADMIN);

        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-token");

        AuthTokenResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("signed-token");
        verify(auditLogService).log(1L, "ADMIN_LOGIN", "Admin logged in successfully");
    }

    @Test
    void loginRejectsBadPassword() {
        LoginRequest request = new LoginRequest("admin", "wrong-password");
        User user = new User();
        user.setUsername("admin");
        user.setPassword("encoded-password");
        user.setRole(UserRole.ADMIN);

        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }
}


