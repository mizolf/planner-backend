package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.ApplyTripTemplateDTO;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.FeaturedTemplateResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import com.mcesnik.planner_backend.responses.TripStyleDetailResponse;
import com.mcesnik.planner_backend.responses.TripStyleResponse;
import com.mcesnik.planner_backend.responses.TripTemplateDetailResponse;
import com.mcesnik.planner_backend.service.TripTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/explore")
public class TripTemplateController {

    private final TripTemplateService templateService;

    public TripTemplateController(TripTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/styles")
    public ResponseEntity<List<TripStyleResponse>> listStyles() {
        return ResponseEntity.ok(templateService.listStyles());
    }

    @GetMapping("/templates")
    public ResponseEntity<List<FeaturedTemplateResponse>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @GetMapping("/styles/{styleSlug}")
    public ResponseEntity<TripStyleDetailResponse> getStyle(@PathVariable String styleSlug) {
        return ResponseEntity.ok(templateService.getStyle(styleSlug));
    }

    @GetMapping("/styles/{styleSlug}/templates/{templateSlug}")
    public ResponseEntity<TripTemplateDetailResponse> getTemplate(
            @PathVariable String styleSlug,
            @PathVariable String templateSlug) {
        return ResponseEntity.ok(templateService.getTemplate(styleSlug, templateSlug));
    }

    @PostMapping("/styles/{styleSlug}/templates/{templateSlug}/apply")
    public ResponseEntity<TripResponse> applyTemplate(
            @PathVariable String styleSlug,
            @PathVariable String templateSlug,
            @Valid @RequestBody ApplyTripTemplateDTO dto) {
        TripResponse response = templateService.applyTemplate(styleSlug, templateSlug, dto, getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/recommended")
    public ResponseEntity<List<FeaturedTemplateResponse>> getRecommendedTemplates() {
        return ResponseEntity.ok(templateService.recommendTemplates(getCurrentUser()));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }
}
