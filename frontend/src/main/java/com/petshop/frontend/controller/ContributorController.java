//Controller class

package com.petshop.frontend.controller;

import com.petshop.frontend.model.ApiCallLog;
import com.petshop.frontend.model.Contributor;
import com.petshop.frontend.service.ApiCallService;
import com.petshop.frontend.service.ContributorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ContributorController {

    private final ContributorService contributorService;
    private final ApiCallService apiCallService;

    public ContributorController(ContributorService contributorService, ApiCallService apiCallService) {
        this.contributorService = contributorService;
        this.apiCallService = apiCallService;
    }

    /** Contributors page — only show the 5 team members (ROLE_ADMIN). */
    @GetMapping("/contributors")
    public String contributors(HttpSession session, Model model) {
        Contributor user = (Contributor) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        model.addAttribute("contributors", contributorService.getAdminContributors());
        model.addAttribute("isAdmin", user.isAdmin());
        return "contributors";
    }

    /** Profile page — pass isAdmin so Thymeleaf can hide/show Test buttons. */
    @GetMapping("/contributors/{id}")
    public String profile(@PathVariable Long id, HttpSession session, Model model) {
        Contributor user = (Contributor) session.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        Contributor contributor = contributorService.getContributorById(id).orElse(null);
        if (contributor == null) return "redirect:/contributors";

        List<ContributorService.Endpoint> endpoints =
                contributorService.getEndpointsForContributor(contributor.getUsername());
        List<ApiCallLog> recentActivity = apiCallService.getRecentActivity(id, 5);

        model.addAttribute("contributor", contributor);
        model.addAttribute("endpoints", endpoints);
        model.addAttribute("recentActivity", recentActivity);
        model.addAttribute("isAdmin", user.isAdmin());
        return "profile";
    }
}

