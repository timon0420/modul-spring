package com.tss.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.websocket.NotificationWebSocketHandler;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AnalysisNotificationService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisNotificationService.class);
    private final NotificationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String gatewayBaseUrl;

    public AnalysisNotificationService(NotificationWebSocketHandler webSocketHandler, ObjectMapper objectMapper,
            @Value("${analysis.gateway.base-url}") String gatewayBaseUrl) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    public void analyzeAndNotify(String login) {
        try {
            String baseUrl = gatewayBaseUrl.endsWith("/")
                    ? gatewayBaseUrl.substring(0, gatewayBaseUrl.length() - 1) : gatewayBaseUrl;
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl).path("/analyze")
                    .queryParam("login", login).build().encode().toUri();
            String body = restTemplate.exchange(RequestEntity.get(uri).build(), String.class).getBody();
            JsonNode notifications = objectMapper.readTree(body).path("new_notifications");
            if (notifications.isArray()) notifications.forEach(notification -> webSocketHandler.send(login, notification));
        } catch (Exception e) {
            log.error("Analiza Go nie powiodła się dla użytkownika {}", login, e);
        }
    }
}
