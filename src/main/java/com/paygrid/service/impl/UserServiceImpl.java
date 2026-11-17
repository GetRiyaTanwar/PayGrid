package com.paygrid.service.impl;

import com.paygrid.dto.request.RegisterRequest;
import com.paygrid.dto.response.UserResponse;
import com.paygrid.entity.Role;
import com.paygrid.entity.User;
import com.paygrid.exception.ResourceNotFoundException;
import com.paygrid.exception.UserAlreadyExistsException;
import com.paygrid.repository.UserRepository;
import com.paygrid.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' is already registered");
        }

        Role role = request.getRole() != null ? request.getRole() : Role.ROLE_USER;
        BigDecimal initialBalance = request.getInitialDeposit() != null && request.getInitialDeposit().compareTo(BigDecimal.ZERO) >= 0
                ? request.getInitialDeposit()
                : BigDecimal.ZERO;

        String accountNumber = generateUniqueAccountNumber();
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getUsername(),
                request.getEmail(),
                hashedPassword,
                role,
                accountNumber,
                initialBalance
        );

        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String username) {
        User user = getUserByUsername(username);
        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByAccountNumber(String accountNumber) {
        return userRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with account number: " + accountNumber));
    }

    @Override
    @Transactional
    public void updateUserBalance(Long userId, BigDecimal newBalance) {
        User user = getUserById(userId);
        user.setBalance(newBalance);
        userRepository.save(user);
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            accountNumber = "PG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (userRepository.findByAccountNumber(accountNumber).isPresent());
        return accountNumber;
    }
}

