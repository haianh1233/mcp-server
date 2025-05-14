package io.condutkor.mcpserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class GraphQLService {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLService.class);

    @Value("${graphql.endpoint:http://localhost:8080/api/api/graphql}")
    private String endpoint;

    @Value("${graphql.allow-mutations:false}")
    private boolean allowMutations;

    @Value("${graphql.schema:}")
    private String schemaPath;

    @Value("${graphql.headers:{}}")
    private String configHeaders;

    @Value("${graphql.introspection-query-path:classpath:graphql/introspection-query.graphql}")
    private String introspectionQueryPath;
    private String introspectionQuery;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final ErrorService errorService;
    private final ResourceLoader resourceLoader;
    private Map<String, String> headers = new HashMap<>();

    public GraphQLService(AuthService authService,
                          RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          ErrorService errorService,
                          ResourceLoader resourceLoader) {
        this.authService = authService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.errorService = errorService;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            this.headers = objectMapper.readValue(configHeaders, HashMap.class);
        } catch (Exception e) {
            logger.error("Error reading config headers", e);
        }

        loadIntrospectionQuery();

        if (authService.isAuthConfigured()) {
            try {
                Map<String, Object> authResponse = authService.login();
                logger.info("Successfully authenticated with the server");

                this.headers.putAll(authService.getAuthHeaders());
            } catch (Exception e) {
                logger.error("Error authenticating with the server", e);
            }
        }
    }

    private void loadIntrospectionQuery() {
        try {
            Resource resource = resourceLoader.getResource(introspectionQueryPath);

            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    introspectionQuery = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                    logger.info("Successfully loaded introspection query");
                }
            } else {
                throw new RuntimeException("Introspection query file not found at " + introspectionQueryPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Introspection query file not found at " + introspectionQueryPath);
        }
    }

    @Tool(description = "Introspect the GraphQL schema, use this tool before doing a query to get the schema information")
    public Map<String, Object> introspectSchema() {
        try {
            String schemaString;
            if (schemaPath != null && !schemaPath.isEmpty()) {
                schemaString = introspectLocalSchema(schemaPath);
            } else {
                schemaString = introspectEndpoint(endpoint);
            }

            Map<String, Object> schemaMap = objectMapper.readValue(schemaString, Map.class);
            return schemaMap;
        } catch (Exception e) {
            return errorService.createError("Failed to introspect schema", e);
        }
    }

    @Tool(description = "Query a GraphQL endpoint with the given query and variables")
    public Map<String, Object> queryGraphQL(String query, String variables) {
        try {
            boolean isMutation = query.trim().toLowerCase().startsWith("mutation");

            if (isMutation && !allowMutations) {
                return errorService.createError("Mutations are not allowed unless you enable them in the configuration.");
            }

            Map<String, Object> variablesMap = new HashMap<>();
            if (variables != null && !variables.isEmpty()) {
                variablesMap = objectMapper.readValue(variables, HashMap.class);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("variables", variablesMap);

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            headers.forEach(httpHeaders::add);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return objectMapper.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            return errorService.createError("Failed to execute GraphQL query", e);
        }
    }

    private String introspectEndpoint(String endpointUrl) throws IOException {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        headers.forEach(httpHeaders::add);

        Map<String, String> body = new HashMap<>();
        body.put("query", introspectionQuery);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(
                endpointUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else {
            throw new RuntimeException("GraphQL request failed: " + response.getStatusCode());
        }
    }

    private String introspectLocalSchema(String path) throws IOException {
        Path filePath = Paths.get(path);
        return new String(Files.readAllBytes(filePath));
    }
}