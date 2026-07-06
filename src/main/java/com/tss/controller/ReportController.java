package com.tss.controller;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Controller
public class ReportController {
    private final String gatewayBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public ReportController(@Value("${analysis.gateway.base-url}") String gatewayBaseUrl) { this.gatewayBaseUrl = gatewayBaseUrl; }

    @GetMapping("/admin/reports/activity.json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadReport() {
        String baseUrl = gatewayBaseUrl.endsWith("/")
                ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1) : gatewayBaseUrl;
        byte[] report = restTemplate.getForObject(URI.create(baseUrl + "/report"), byte[].class);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activity-report.json")
                .body(report);
    }
}
