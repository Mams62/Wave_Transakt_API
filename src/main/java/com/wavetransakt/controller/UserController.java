package com.wavetransakt.controller;

import com.wavetransakt.model.User;
import com.wavetransakt.model.Wallet;
import com.wavetransakt.repository.UserRepository;
import com.wavetransakt.repository.WalletRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public UserController(UserRepository userRepository, WalletRepository walletRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        User user = new User();
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setWalletNumber("9" + String.format("%09d", System.nanoTime() % 1_000_000_000L));
        user = userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        walletRepository.save(wallet);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> get(@PathVariable UUID id) {
        return userRepository.findById(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateUserRequest(String email, String fullName) {}
}
