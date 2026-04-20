package com.petshop.frontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.frontend.model.Contributor;
import com.petshop.frontend.service.ContributorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Controller
public class AuthController {

    private final ContributorService contributorService;
    private final String backendBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthController(ContributorService contributorService,
                          @Value("${backend.base.url}") String backendBaseUrl) {
        this.contributorService = contributorService;
        this.backendBaseUrl = backendBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @GetMapping({"/", "/login"})
    public String loginPage(HttpSession session) {
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/contributors";
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {

        Contributor contributor = contributorService.authenticate(username, password);
        if (contributor == null) {
            model.addAttribute("error", "Invalid username or password.");
            return "login";
        }

        // Fetch a JWT that carries the user's role as a claim
        String jwtToken = fetchBackendToken(username, password, contributor.getUserRole());

        session.setAttribute("loggedInUser", contributor);
        if (jwtToken != null) {
            session.setAttribute("jwtToken", jwtToken);
        }
        return "redirect:/contributors";
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerPage(HttpSession session) {
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/contributors";
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam String name,
                           HttpSession session,
                           Model model) {

        if (username.isBlank() || password.isBlank() || name.isBlank()) {
            model.addAttribute("error", "All fields are required.");
            return "register";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }

        if (password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            return "register";
        }

        Contributor created = contributorService.register(username.trim(), password, name.trim());
        if (created == null) {
            model.addAttribute("error", "Username \"" + username + "\" is already taken.");
            return "register";
        }

        // Auto-login after successful registration (ROLE_USER, no JWT needed for GET-only)
        session.setAttribute("loggedInUser", created);
        // ROLE_USER — no JWT token stored; POST/PUT will be blocked in the UI anyway
        return "redirect:/contributors";
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Calls POST /api/auth/login on the backend with username + password + role.
     * The backend embeds the role in the JWT so SecurityConfig can enforce ROLE_ADMIN
     * for POST/PUT endpoints.
     */
    private String fetchBackendToken(String username, String password, String userRole) {
        try {
            String jsonBody = String.format(
                    "{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}",
                    username, password, userRole);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendBaseUrl + "/api/auth/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("token")) {
                    return node.get("token").asText();
                }
            }
        } catch (Exception ignored) {
            // Backend unreachable — user can still browse GET endpoints
        }
        return null;
    }
}
