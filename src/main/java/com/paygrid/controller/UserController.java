package com.paygrid.controller;

import com.paygrid.dto.response.UserResponse;
import com.paygrid.entity.Role;
import com.paygrid.entity.User;
import com.paygrid.exception.UnauthorizedAccessException;
import com.paygrid.security.CustomUserDetails;
import com.paygrid.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Users", description = "User profile and account operations")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Returns the profile of the currently logged-in user")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = userService.getUserProfile(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Returns user details. Users can only access their own details unless they have ROLE_ADMIN.")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // Enforce ownership check: users can only view their own user entity unless ADMIN
        if (!userDetails.getId().equals(id) && userDetails.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("You are not authorized to view another user's profile");
        }

        User user = userService.getUserById(id);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Find user by account number", description = "Allows looking up account holder information by account number for transfers")
    public ResponseEntity<UserResponse> getUserByAccountNumber(@PathVariable String accountNumber) {
        User user = userService.getUserByAccountNumber(accountNumber);
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }
}

