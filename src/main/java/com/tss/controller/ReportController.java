package com.tss.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

@Controller
public class ReportController {
    private final String gatewayBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public ReportController(@Value("${analysis.gateway.base-url}") String gatewayBaseUrl) { this.gatewayBaseUrl = gatewayBaseUrl; }

    @GetMapping("/admin/reports/activity.json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadReport() {
        byte[] report = restTemplate.getForObject(gatewayUri("/report"), byte[].class);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activity-report.json")
                .body(report);
    }

    @PostMapping("/admin/reports/import")
    @PreAuthorize("hasRole('ADMIN')")
    public String importData(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("adminError", "Wybierz plik JSON do importu.");
            return "redirect:/";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<byte[]> request = new HttpEntity<>(file.getBytes(), headers);
            restTemplate.postForEntity(gatewayUri("/import"), request, String.class);
            redirectAttributes.addFlashAttribute("adminSuccess", "Dane z pliku JSON zostały zaimportowane do MongoDB.");
        } catch (IOException | RestClientException ex) {
            redirectAttributes.addFlashAttribute("adminError", "Nie udało się zaimportować danych: " + ex.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/admin/reports/database/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public String clearDatabase(RedirectAttributes redirectAttributes) {
        try {
            restTemplate.exchange(gatewayUri("/users"), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
            redirectAttributes.addFlashAttribute("adminSuccess", "Baza MongoDB została wyczyszczona.");
        } catch (RestClientException ex) {
            redirectAttributes.addFlashAttribute("adminError", "Nie udało się wyczyścić bazy: " + ex.getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/admin/reports/users/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteUser(@RequestParam String login, RedirectAttributes redirectAttributes) {
        if (login == null || login.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("adminError", "Podaj login użytkownika do usunięcia.");
            return "redirect:/";
        }

        try {
            String encodedLogin = UriUtils.encodePathSegment(login.trim(), StandardCharsets.UTF_8);
            ResponseEntity<Map<String, Integer>> response = restTemplate.exchange(
                    gatewayUri("/users/" + encodedLogin),
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<Map<String, Integer>>() {});
            Integer deleted = response.getBody() == null ? 0 : response.getBody().getOrDefault("deleted", 0);
            if (deleted > 0) {
                redirectAttributes.addFlashAttribute("adminSuccess", "Użytkownik " + login.trim() + " został usunięty z MongoDB.");
            } else {
                redirectAttributes.addFlashAttribute("adminSuccess", "W MongoDB nie znaleziono danych dla loginu " + login.trim() + ".");
            }
        } catch (RestClientException ex) {
            redirectAttributes.addFlashAttribute("adminError", "Nie udało się usunąć użytkownika: " + ex.getMessage());
        }

        return "redirect:/";
    }

    private URI gatewayUri(String path) {
        String baseUrl = gatewayBaseUrl.endsWith("/")
                ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1) : gatewayBaseUrl;
        return URI.create(baseUrl + path);
    }
}
