package com.petshop.frontend.service;

import com.petshop.frontend.model.Contributor;
import com.petshop.frontend.repository.ContributorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ContributorService {

    private final ContributorRepository contributorRepository;

    public ContributorService(ContributorRepository contributorRepository) {
        this.contributorRepository = contributorRepository;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public Contributor authenticate(String username, String password) {
        Optional<Contributor> contributor = contributorRepository.findByUsername(username);
        if (contributor.isPresent() && contributor.get().getPassword().equals(password)) {
            return contributor.get();
        }
        return null;
    }

    /**
     * Registers a brand-new ROLE_USER account.
     * Returns null if the username is already taken.
     */
    public Contributor register(String username, String password, String name) {
        if (contributorRepository.findByUsername(username).isPresent()) {
            return null; // username taken
        }
        Contributor c = new Contributor();
        c.setUsername(username);
        c.setPassword(password);
        c.setName(name);
        c.setRole("Member");
        c.setDescription("Registered user");
        c.setProfilePicUrl("/images/default.png");
        c.setUserRole("ROLE_USER");
        return contributorRepository.save(c);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Contributor> getAllContributors() {
        return contributorRepository.findAll();
    }

    /** Returns only ROLE_ADMIN contributors (the team members on the contributors page). */
    public List<Contributor> getAdminContributors() {
        return contributorRepository.findAll().stream()
                .filter(Contributor::isAdmin)
                .toList();
    }

    public Optional<Contributor> getContributorById(Long id) {
        return contributorRepository.findById(id);
    }

    // ── Endpoint catalogue (unchanged) ────────────────────────────────────────

    public List<Endpoint> getEndpointsForContributor(String contributorUsername) {
        List<Endpoint> endpoints;
        switch (contributorUsername.toLowerCase()) {
            case "sharvari":
                endpoints = List.of(
                        new Endpoint("POST", "/api/v1/customers/register"),
                        new Endpoint("POST", "/api/v1/customers/login"),
                        new Endpoint("GET",  "/api/v1/customers/{id}"),
                        new Endpoint("PUT",  "/api/v1/customers/{id}"),
                        new Endpoint("GET",  "/api/v1/customers/{id}/address"),
                        new Endpoint("POST", "/api/v1/customers/{id}/address"),
                        new Endpoint("PUT", "/api/v1/customers/addresses/{id}")
                );
                break;
            case "yatesh":
                endpoints = List.of(
                        new Endpoint("GET",  "/api/v1/inventory/food"),
                        new Endpoint("GET",  "/api/v1/inventory/food/{id}"),
                        new Endpoint("POST", "/api/v1/inventory/food"),
                        new Endpoint("PUT",  "/api/v1/inventory/food/{id}"),
                        new Endpoint("GET",  "/api/v1/inventory/suppliers"),
                        new Endpoint("GET",  "/api/v1/inventory/suppliers/{id}"),
                        new Endpoint("POST", "/api/v1/inventory/suppliers"),
                        new Endpoint("PUT",  "/api/v1/inventory/suppliers/{id}"),
                        new Endpoint("GET",  "/api/v1/inventory/employees"),
                        new Endpoint("GET",  "/api/v1/inventory/employees/{id}"),
                        new Endpoint("POST", "/api/v1/inventory/employees"),
                        new Endpoint("PUT",  "/api/v1/inventory/employees/{id}"),
                        new Endpoint("POST", "/api/v1/inventory/pets/{petId}/food/{foodId}"),
                        new Endpoint("POST", "/api/v1/inventory/pets/{petId}/suppliers/{supplierId}"),
                        new Endpoint("POST", "/api/v1/inventory/pets/{petId}/employees/{employeeId}"),
                        new Endpoint("GET",  "/api/v1/inventory/pets/{petId}/food"),
                        new Endpoint("GET",  "/api/v1/inventory/pets/{petId}/suppliers"),
                        new Endpoint("GET",  "/api/v1/inventory/pets/{petId}/employees")
                );
                break;
            case "sushmita":
                endpoints = List.of(
                        new Endpoint("GET",  "/api/v1/categories"),
                        new Endpoint("GET",  "/api/v1/categories/{id}"),
                        new Endpoint("POST", "/api/v1/categories"),
                        new Endpoint("PUT",  "/api/v1/categories/{id}"),
                        new Endpoint("GET",  "/api/v1/pets"),
                        new Endpoint("GET",  "/api/v1/pets/{id}"),
                        new Endpoint("POST", "/api/v1/pets"),
                        new Endpoint("PUT",  "/api/v1/pets/{id}"),
                        new Endpoint("GET",  "/api/v1/pets/categories/{categoryId}"),
                        new Endpoint("GET",  "/api/v1/pets/{id}/details")
                );
                break;
            case "tejas":
                endpoints = List.of(
                        new Endpoint("GET",  "/api/v1/services/grooming"),
                        new Endpoint("POST", "/api/v1/services/grooming"),
                        new Endpoint("POST", "/api/v1/services/pets/{petId}/grooming/{serviceId}"),
                        new Endpoint("GET",  "/api/v1/services/pets/{petId}/grooming"),
                        new Endpoint("GET",  "/api/v1/services/vaccinations"),
                        new Endpoint("POST", "/api/v1/services/vaccinations"),
                        new Endpoint("POST", "/api/v1/services/pets/{petId}/vaccinations/{vaccinationId}"),
                        new Endpoint("GET",  "/api/v1/services/pets/{petId}/vaccinations")
                );
                break;
            case "siddhant":
                endpoints = List.of(
                        new Endpoint("POST", "/api/v1/transactions/orders"),
                        new Endpoint("GET",  "/api/v1/transactions/orders/{id}"),
                        new Endpoint("GET",  "/api/v1/transactions/customers/{id}/orders"),
                        new Endpoint("GET",  "/api/v1/transactions/{id}"),
                        new Endpoint("GET",  "/api/v1/transactions/customers/{id}"),
                        new Endpoint("GET",  "/api/v1/transactions"),
                        new Endpoint("PUT",  "/api/v1/transactions/{id}/status")
                );
                break;
            default:
                endpoints = List.of();
        }
        return endpoints;
    }

    public int getTotalEndpointsForContributor(String contributorUsername) {
        return getEndpointsForContributor(contributorUsername).size();
    }

    public static class Endpoint {
        private final String method;
        private final String path;

        public Endpoint(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public String getMethod() { return method; }
        public String getPath()   { return path;   }
    }
}
