package com.instakill.analytics.api.rest;

import com.instakill.analytics.application.AnalyticsService;
import com.instakill.analytics.domain.UserAnalytics;
import com.instakill.infrastructure.security.JwtUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsRestController {

    private final AnalyticsService analyticsService;

    public AnalyticsRestController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/me")
    public UserAnalytics me(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return analyticsService.getForUser(principal.id());
    }
}
