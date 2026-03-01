package com.cryptointel.services;

import com.cryptointel.models.User;
import com.cryptointel.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", user.getId());
                    m.put("email", user.getEmail());
                    m.put("name", user.getName());
                    m.put("role", user.getRole().name());
                    m.put("createdAt", user.getCreatedAt().toString());
                    return m;
                })
                .toList();
    }
}
