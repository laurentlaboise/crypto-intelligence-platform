package com.cryptointel.services;

import com.cryptointel.models.User;
import com.cryptointel.models.UserBalance;
import com.cryptointel.repositories.UserBalanceRepository;
import com.cryptointel.repositories.UserRepository;
import com.cryptointel.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserBalanceRepository balanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final BigDecimal defaultBalance;

    public AuthService(UserRepository userRepository,
                       UserBalanceRepository balanceRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       @Value("${app.default-balance}") BigDecimal defaultBalance) {
        this.userRepository = userRepository;
        this.balanceRepository = balanceRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.defaultBalance = defaultBalance;
    }

    @Transactional
    public Map<String, Object> register(String email, String password, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name);
        user.setRole(User.Role.USER);
        user = userRepository.save(user);

        UserBalance balance = new UserBalance();
        balance.setUser(user);
        balance.setBalance(defaultBalance);
        balanceRepository.save(balance);

        return Map.of(
            "id", user.getId(),
            "email", user.getEmail(),
            "role", user.getRole().name(),
            "createdAt", user.getCreatedAt().toString()
        );
    }

    public Map<String, Object> login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getRole().name());

        return Map.of(
            "token", token,
            "role", user.getRole().name(),
            "expiresIn", tokenProvider.getExpirationSeconds()
        );
    }

    public Map<String, Object> validate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return Map.of(
            "valid", true,
            "userId", user.getId(),
            "role", user.getRole().name()
        );
    }
}
