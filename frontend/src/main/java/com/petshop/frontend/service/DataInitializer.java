package com.petshop.frontend.service;

import com.petshop.frontend.model.Contributor;
import com.petshop.frontend.repository.ContributorRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ContributorRepository contributorRepository;

    public DataInitializer(ContributorRepository contributorRepository) {
        this.contributorRepository = contributorRepository;
    }

    @Override
    public void run(String... args) {
        // These 5 team members get ROLE_ADMIN — they can call POST + PUT endpoints
        upsertAdmin("sharvari", "Sharvari Patil",  "Backend Developer", "Customers & Addresses API", "/images/sharvari.jpeg");
        upsertAdmin("yatesh",   "Yatesh Ahire",    "Backend Developer", "Inventory API",             "/images/yatesh.jpeg");
        upsertAdmin("sushmita", "Sushmita Damarla", "Backend Developer", "Pets & Categories API",    "/images/sushmita.jpeg");
        upsertAdmin("tejas",    "Tejas Daphal",    "Backend Developer", "Services API",              "/images/tejas.jpeg");
        upsertAdmin("siddhant", "Siddhant Narkar",  "Backend Developer", "Transactions API",         "/images/siddhant.jpeg");
    }

    private void upsertAdmin(String username, String fullName, String role,
                              String description, String profilePicUrl) {
        Contributor c = contributorRepository.findByUsername(username).orElseGet(Contributor::new);
        c.setUsername(username);
        c.setPassword("password123");
        c.setName(fullName);
        c.setRole(role);
        c.setDescription(description);
        c.setProfilePicUrl(profilePicUrl);
        c.setUserRole("ROLE_ADMIN");   // ← team members are admins
        contributorRepository.save(c);
    }
}

