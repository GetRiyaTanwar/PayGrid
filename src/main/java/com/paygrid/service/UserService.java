package com.paygrid.service;

import com.paygrid.dto.request.RegisterRequest;
import com.paygrid.dto.response.UserResponse;
import com.paygrid.entity.User;

import java.math.BigDecimal;

public interface UserService {

    UserResponse register(RegisterRequest request);

    User getUserByUsername(String username);

    User getUserById(Long id);

    UserResponse getUserProfile(String username);

    User getUserByAccountNumber(String accountNumber);

    void updateUserBalance(Long userId, BigDecimal newBalance);
}

