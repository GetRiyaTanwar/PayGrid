package com.paygrid.service.impl;

import com.paygrid.dto.request.LoginRequest;
import com.paygrid.dto.request.RegisterRequest;
import com.paygrid.dto.response.AuthResponse;
import com.paygrid.dto.response.UserResponse;
import com.paygrid.entity.User;
import com.paygrid.exception.InvalidCredentialsException;
import com.paygrid.repository.UserRepository;
import com.paygrid.security.CustomUserDetails;
import com.paygrid.security.JwtTokenProvider;
import com.paygrid.service.AuthService;
import com.paygrid.service.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserService userService,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        return userService.register(request);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String token = jwtTokenProvider.generateToken(authentication);

            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new InvalidCredentialsException("User not found"));

            return new AuthResponse(
                    token,
                    jwtTokenProvider.getExpirationMs(),
                    UserResponse.fromEntity(user)
            );
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid username or password");
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Authentication failed: " + ex.getMessage());
        }
    }

    @Override
    public UserResponse getCurrentUser(String username) {
        return userService.getUserProfile(username);
    }
}

