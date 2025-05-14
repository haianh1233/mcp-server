package io.condutkor.mcpserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    @Value("${auth.login-endpoint:}")
    private String loginEndpoint;

    @Value("${auth.username:}")
    private String username;

    @Value("${auth.password:}")
    private String password;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private String accessToken;

    public AuthService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> login() {
        if (loginEndpoint == null || loginEndpoint.isEmpty() ||
                username == null || username.isEmpty() ||
                password == null || password.isEmpty()) {
            throw new RuntimeException("Login credentials not configured");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("username", username);
            requestBody.put("password", password);

            String jsonRequestBody = objectMapper.writeValueAsString(requestBody);

            HttpEntity<String> entity = new HttpEntity<>(jsonRequestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    loginEndpoint,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> authResponse = objectMapper.readValue(response.getBody(), HashMap.class);
                this.accessToken = (String) authResponse.get("access_token");
                return authResponse;
            } else {
                throw new RuntimeException("Login failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Authentication failed: " + e.getMessage());
        }
    }

    public Map<String, String> getAuthHeaders() {
        if (accessToken == null || accessToken.isEmpty()) {
            try {
                login();
            } catch (Exception e) {
                return new HashMap<>();
            }
        }
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        return headers;
    }
    
    public boolean isAuthConfigured() {
        return loginEndpoint != null && !loginEndpoint.isEmpty() &&
               username != null && !username.isEmpty() &&
               password != null && !password.isEmpty();
    }
}