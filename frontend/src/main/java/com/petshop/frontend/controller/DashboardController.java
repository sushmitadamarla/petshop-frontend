package com.petshop.frontend.controller;

import com.petshop.frontend.model.Contributor;
import com.petshop.frontend.model.ApiCallLog;
import com.petshop.frontend.service.DashboardService;
import com.petshop.frontend.service.ApiCallService;
import com.petshop.frontend.service.ContributorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final ApiCallService apiCallService;
    private final ContributorService contributorService;

    public DashboardController(DashboardService dashboardService, ApiCallService apiCallService, ContributorService contributorService) {
        this.dashboardService = dashboardService;
        this.apiCallService = apiCallService;
        this.contributorService = contributorService;
    }

    @GetMapping("/dashboard")
    public String dashboard(
            HttpSession session,
            Model model,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Long contributorId) {

        Contributor user = (Contributor) session.getAttribute("loggedInUser");
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("totalApiCalls", dashboardService.getTotalApiCalls());
        model.addAttribute("successfulCalls", dashboardService.getSuccessfulCalls());
        model.addAttribute("failedCalls", dashboardService.getFailedCalls());
        model.addAttribute("processingCalls", dashboardService.getProcessingCalls());

        model.addAttribute("statusDistribution", dashboardService.getStatusDistribution());
        model.addAttribute("contributorApiCalls", dashboardService.getContributorApiCalls());
        model.addAttribute("httpMethodDistribution", dashboardService.getHttpMethodDistribution());
        model.addAttribute("topEndpoints", dashboardService.getTopEndpoints());

        List<DashboardService.ContributorStats> contributorStats = dashboardService.getContributorStats();
        model.addAttribute("contributorStats", contributorStats);

        List<Contributor> allContributors = contributorService.getAllContributors();
        model.addAttribute("allContributors", allContributors);

        Map<Long, String> contributorNames = new LinkedHashMap<>();
        for (Contributor c : allContributors) {
            contributorNames.put(c.getId(), c.getName());
        }
        model.addAttribute("contributorNames", contributorNames);

        int pageSize = 10;
        long totalCount;
        List<ApiCallLog> recentActivity;

        if (contributorId != null) {
            totalCount = apiCallService.getTotalActivityCountByContributor(contributorId);
            recentActivity = apiCallService.getPaginatedActivityByContributor(contributorId, page, pageSize);
        } else {
            totalCount = apiCallService.getTotalActivityCount();
            recentActivity = apiCallService.getPaginatedActivity(page, pageSize);
        }

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        model.addAttribute("recentActivity", recentActivity);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalActivity", totalCount);
        model.addAttribute("selectedContributorId", contributorId);

        return "dashboard";
    }
}