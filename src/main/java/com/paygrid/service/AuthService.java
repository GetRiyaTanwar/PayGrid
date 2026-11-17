package com.paygrid.service;

import com.paygrid.dto.request.LoginRequest;
import com.paygrid.dto.request.RegisterRequest;
import com.paygrid.dto.response.AuthResponse;
import com.paygrid.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserResponse getCurrentUser(String username);
}

