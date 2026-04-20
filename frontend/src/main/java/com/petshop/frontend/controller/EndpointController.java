package com.petshop.frontend.controller;

import com.petshop.frontend.model.Contributor;
import com.petshop.frontend.service.ApiCallService;
import com.petshop.frontend.service.ContributorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class EndpointController {

    private final ContributorService contributorService;
    private final ApiCallService apiCallService;

    public EndpointController(ContributorService contributorService, ApiCallService apiCallService) {
        this.contributorService = contributorService;
        this.apiCallService = apiCallService;
    }

    @GetMapping("/endpoint/{contributorId}")
    public String endpointPage(@PathVariable Long contributorId,
                                @RequestParam String path,
                                @RequestParam String method,
                                HttpSession session,
                                Model model) {
        Contributor user = (Contributor) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        Contributor contributor = contributorService.getContributorById(contributorId).orElse(null);
        if (contributor == null) return "redirect:/contributors";

        String normalizedMethod = method.toUpperCase();

        // ROLE_USER cannot access POST / PUT at all — show access-denied message
        if (!user.isAdmin() && !"GET".equals(normalizedMethod)) {
            model.addAttribute("contributor", contributor);
            model.addAttribute("method", normalizedMethod);
            model.addAttribute("path", path);
            model.addAttribute("accessDenied", true);
            model.addAttribute("accessDeniedMessage",
                    "You need an admin account to call " + normalizedMethod + " endpoints. " +
                    "Please log in with a team-member account.");
            return templateForMethod(normalizedMethod);
        }

        model.addAttribute("contributor", contributor);
        model.addAttribute("method", normalizedMethod);
        model.addAttribute("isAdmin", user.isAdmin());
        populateFormModel(model, path, normalizedMethod, new LinkedHashMap<>(), new LinkedHashMap<>());
        return templateForMethod(normalizedMethod);
    }

    @PostMapping("/endpoint/{contributorId}")
    public String executeEndpoint(@PathVariable Long contributorId,
                                   @RequestParam Map<String, String> formData,
                                   HttpSession session,
                                   Model model) {
        Contributor user = (Contributor) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        Contributor contributor = contributorService.getContributorById(contributorId).orElse(null);
        if (contributor == null) return "redirect:/contributors";

        String pathTemplate    = formData.getOrDefault("path", "");
        String normalizedMethod = formData.getOrDefault("method", "GET").toUpperCase();

        // Double-check: ROLE_USER must not reach here for POST/PUT
        if (!user.isAdmin() && !"GET".equals(normalizedMethod)) {
            model.addAttribute("contributor", contributor);
            model.addAttribute("method", normalizedMethod);
            model.addAttribute("path", pathTemplate);
            model.addAttribute("accessDenied", true);
            model.addAttribute("accessDeniedMessage",
                    "You need an admin account to call " + normalizedMethod + " endpoints.");
            return templateForMethod(normalizedMethod);
        }

        String jwtToken = (String) session.getAttribute("jwtToken");

        Map<String, String> pathValues = apiCallService.extractValuesByPrefix(formData, "path_");
        Map<String, String> bodyValues = apiCallService.extractValuesByPrefix(formData, "body_");

        ApiCallService.ApiResponse response;
        String resolvedPath = pathTemplate;
        String requestBody  = "{}";

        try {
            resolvedPath = apiCallService.resolvePathTemplate(pathTemplate, pathValues);
            requestBody  = apiCallService.buildRequestBody(pathTemplate, normalizedMethod, bodyValues);

            response = switch (normalizedMethod) {
                case "GET"  -> apiCallService.executeGet(resolvedPath);
                case "POST" -> apiCallService.executePost(resolvedPath, requestBody, jwtToken);
                case "PUT"  -> apiCallService.executePut(resolvedPath, requestBody, jwtToken);
                default     -> new ApiCallService.ApiResponse(400, "Unsupported method", "FAILED");
            };
        } catch (IllegalArgumentException ex) {
            response = new ApiCallService.ApiResponse(400, ex.getMessage(), "FAILED");
        }

        String loggedBody = "GET".equals(normalizedMethod) ? null : requestBody;
        apiCallService.saveApiCallLog(contributorId, resolvedPath, normalizedMethod,
                loggedBody, response.body(), response.status(), response.statusCode());

        model.addAttribute("contributor", contributor);
        model.addAttribute("method", normalizedMethod);
        model.addAttribute("isAdmin", user.isAdmin());
        model.addAttribute("resolvedPath", resolvedPath);
        model.addAttribute("statusCode", response.statusCode());
        model.addAttribute("responseBody", response.body());
        populateFormModel(model, pathTemplate, normalizedMethod, pathValues, bodyValues);

        List<Map<String, Object>> tableRows = apiCallService.parseJsonToTableRows(response.body(), resolvedPath);
        List<String> tableHeaders = apiCallService.getTableHeaders(tableRows);
        model.addAttribute("tableRows", tableRows);
        model.addAttribute("tableHeaders", tableHeaders);

        return templateForMethod(normalizedMethod);
    }

    private void populateFormModel(Model model, String pathTemplate, String method,
                                   Map<String, String> pathValues, Map<String, String> bodyValues) {
        ApiCallService.EndpointFormSpec formSpec = apiCallService.getFormSpec(pathTemplate, method);
        model.addAttribute("path", pathTemplate);
        model.addAttribute("pathParams", formSpec.pathParams());
        model.addAttribute("bodyFields", formSpec.bodyFields());
        model.addAttribute("pathValues", pathValues);
        model.addAttribute("bodyValues", bodyValues);
    }

    private String templateForMethod(String method) {
        return "GET".equalsIgnoreCase(method) ? "endpoint-get" : "endpoint-post";
    }
}
