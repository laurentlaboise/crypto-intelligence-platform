package com.cryptointel.controllers;

import com.cryptointel.services.AdminService;
import com.cryptointel.services.TradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final TradingService tradingService;

    public AdminController(AdminService adminService, TradingService tradingService) {
        this.adminService = adminService;
        this.tradingService = tradingService;
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getAllTransactions() {
        return ResponseEntity.ok(tradingService.getAllTransactions());
    }
}
