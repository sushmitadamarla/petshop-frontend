package com.petshop.frontend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.frontend.model.ApiCallLog;
import com.petshop.frontend.repository.ApiCallLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ApiCallService {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^/{}]+)}");

    private final HttpClient httpClient;
    private final String backendBaseUrl;
    private final ApiCallLogRepository apiCallLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiCallService(@Value("${backend.base.url}") String backendBaseUrl,
                          ApiCallLogRepository apiCallLogRepository) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.backendBaseUrl = backendBaseUrl;
        this.apiCallLogRepository = apiCallLogRepository;
    }

    public ApiResponse executeGet(String path) {
        return execute("GET", path, null, null);
    }

    /** POST with Bearer token (required by backend for authorized endpoints). */
    public ApiResponse executePost(String path, String jsonBody, String jwtToken) {
        return execute("POST", path, jsonBody, jwtToken);
    }

    /** PUT with Bearer token (required by backend for authorized endpoints). */
    public ApiResponse executePut(String path, String jsonBody, String jwtToken) {
        return execute("PUT", path, jsonBody, jwtToken);
    }

    // Legacy no-token overloads kept for backwards compatibility
    public ApiResponse executePost(String path, String jsonBody) {
        return execute("POST", path, jsonBody, null);
    }

    public ApiResponse executePut(String path, String jsonBody) {
        return execute("PUT", path, jsonBody, null);
    }

    private ApiResponse execute(String method, String path, String jsonBody, String jwtToken) {
        String normalizedPath = normalizePath(path);
        String url = backendBaseUrl + normalizedPath;

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            String body = jsonBody == null ? "{}" : jsonBody;

            switch (method) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
                case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body));
                default -> {
                    return new ApiResponse(400, "Unsupported method", "FAILED");
                }
            }

            requestBuilder.header("Content-Type", "application/json");

            // Forward JWT Bearer token for protected POST/PUT endpoints
            if (jwtToken != null && !jwtToken.isBlank()) {
                String bearerHeader = jwtToken.startsWith("Bearer ") ? jwtToken : "Bearer " + jwtToken;
                requestBuilder.header("Authorization", bearerHeader);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            String responseBody = response.body() != null ? response.body() : "";
            boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 400;
            
            return new ApiResponse(
                    response.statusCode(),
                    responseBody,
                    isSuccess ? "SUCCESS" : "FAILED"
            );
        } catch (Exception e) {
            return new ApiResponse(500, e.getMessage(), "FAILED");
        }
    }

    public EndpointFormSpec getFormSpec(String path, String method) {
        String normalizedPath = normalizePath(path);
        String normalizedMethod = method == null ? "GET" : method.trim().toUpperCase();
        List<String> pathParams = extractPathParams(normalizedPath);
        List<FormField> bodyFields = getBodyFields(normalizedPath, normalizedMethod);
        return new EndpointFormSpec(pathParams, bodyFields);
    }

    public Map<String, String> extractValuesByPrefix(Map<String, String> formData, String prefix) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                values.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return values;
    }

    public String resolvePathTemplate(String pathTemplate, Map<String, String> pathValues) {
        String resolved = normalizePath(pathTemplate);
        for (String pathParam : extractPathParams(resolved)) {
            String rawValue = pathValues.get(pathParam);
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalArgumentException("Please provide value for path parameter: " + pathParam);
            }
            String encoded = URLEncoder.encode(rawValue.trim(), StandardCharsets.UTF_8);
            resolved = resolved.replace("{" + pathParam + "}", encoded);
        }
        return resolved;
    }

    public String buildRequestBody(String pathTemplate, String method, Map<String, String> bodyValues) {
        List<FormField> bodyFields = getBodyFields(normalizePath(pathTemplate), method == null ? "GET" : method.toUpperCase());
        if (bodyFields.isEmpty()) {
            return "{}";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        for (FormField field : bodyFields) {
            String rawValue = bodyValues.get(field.key());
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            payload.put(field.key(), convertType(rawValue.trim(), field.type()));
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    public void saveApiCallLog(Long contributorId, String endpoint, String httpMethod,
                               String requestBody, String responseBody, String status, Integer statusCode) {
        ApiCallLog log = new ApiCallLog();
        log.setContributorId(contributorId);
        log.setEndpoint(endpoint);
        log.setHttpMethod(httpMethod);
        log.setRequestBody(requestBody);
        log.setResponseBody(responseBody);
        log.setStatus(status);
        log.setStatusCode(statusCode);
        log.setCalledAt(LocalDateTime.now());
        apiCallLogRepository.save(log);
    }

    public List<ApiCallLog> getRecentActivity(Long contributorId, int limit) {
        List<ApiCallLog> logs = apiCallLogRepository.findByContributorIdOrderByCalledAtDesc(contributorId);
        return logs.stream().limit(limit).toList();
    }

    public List<ApiCallLog> getAllRecentActivity(int limit) {
        List<ApiCallLog> logs = apiCallLogRepository.findTop20ByOrderByCalledAtDesc();
        return logs.stream().limit(limit).toList();
    }

    public List<ApiCallLog> getPaginatedActivity(int page, int size) {
        int offset = (page - 1) * size;
        return apiCallLogRepository.findPaginated(offset, size);
    }

    public List<ApiCallLog> getPaginatedActivityByContributor(Long contributorId, int page, int size) {
        int offset = (page - 1) * size;
        return apiCallLogRepository.findByContributorIdPaginated(contributorId, offset, size);
    }

    public long getTotalActivityCount() {
        Long count = apiCallLogRepository.countAll();
        return count != null ? count : 0;
    }

    public long getTotalActivityCountByContributor(Long contributorId) {
        Long count = apiCallLogRepository.countByContributorId(contributorId);
        return count != null ? count : 0;
    }

    public List<Map<String, Object>> parseJsonToTableRows(String jsonResponse, String endpoint) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return result;
        }

        try {
            String trimmedResponse = jsonResponse.trim();
            if (trimmedResponse.equals("[]")) {
                return result;
            }
            JsonNode root = objectMapper.readTree(trimmedResponse);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.isObject()) {
                        result.add(objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {}));
                    } else {
                        Map<String, Object> scalarMap = new LinkedHashMap<>();
                        scalarMap.put("value", node.asText());
                        result.add(scalarMap);
                    }
                }
            } else if (root.isObject()) {
                Map<String, Object> map = objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
                if (map.containsKey("transactions") && map.get("transactions") instanceof List) {
                    List<Map<String, Object>> nestedList = (List<Map<String, Object>>) map.get("transactions");
                    if (!nestedList.isEmpty()) {
                        result.add(map);
                    }
                } else {
                    result.add(map);
                }
            } else {
                Map<String, Object> scalarMap = new LinkedHashMap<>();
                scalarMap.put("value", root.asText());
                result.add(scalarMap);
            }
        } catch (Exception e) {
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("response", jsonResponse);
            result.add(errorMap);
        }
        return result;
    }

    public List<Map<String, Object>> parseJsonToTableRows(String jsonResponse) {
        return parseJsonToTableRows(jsonResponse, null);
    }

    public List<String> getTableHeaders(List<Map<String, Object>> tableRows) {
        Set<String> headers = new LinkedHashSet<>();
        for (Map<String, Object> row : tableRows) {
            headers.addAll(row.keySet());
        }
        return new ArrayList<>(headers);
    }

    private List<String> extractPathParams(String pathTemplate) {
        List<String> params = new ArrayList<>();
        Matcher matcher = PATH_PARAM_PATTERN.matcher(pathTemplate);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private Object convertType(String value, String type) {
        return switch (type) {
            case "number" -> {
                try {
                    yield Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    yield value;
                }
            }
            case "decimal" -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    yield value;
                }
            }
            case "boolean" -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    private List<FormField> getBodyFields(String path, String method) {
        String key = method + " " + path;

        return switch (key) {
            case "POST /api/v1/customers/register", "PUT /api/v1/customers/{id}" -> List.of(
                    field("firstName"),
                    field("lastName"),
                    field("email"),
                    field("phone")
            );
            case "POST /api/v1/customers/login" -> List.of(
                    field("email"),
                    field("phone")
            );
            case "POST /api/v1/customers/{id}/address",
                 "PUT /api/v1/customers/addresses/{id}" -> List.of(
                    field("street"),
                    field("city"),
                    field("state"),
                    field("pincode")
            );
            case "POST /api/v1/inventory/food", "PUT /api/v1/inventory/food/{id}" -> List.of(
                    field("name"),
                    field("brand"),
                    field("type"),
                    field("quantity", "number"),
                    field("price", "decimal")
            );
            case "POST /api/v1/inventory/suppliers", "PUT /api/v1/inventory/suppliers/{id}" -> List.of(
                    field("name"),
                    field("contactPerson"),
                    field("phoneNumber"),
                    field("email")
            );
            case "POST /api/v1/inventory/employees", "PUT /api/v1/inventory/employees/{id}" -> List.of(
                    field("firstName"),
                    field("lastName"),
                    field("position"),
                    field("phoneNumber"),
                    field("email"),
                    field("hireDate")
            );
            case "POST /api/v1/categories", "PUT /api/v1/categories/{id}" -> List.of(
                    field("name")
            );
            case "POST /api/v1/pets", "PUT /api/v1/pets/{id}" -> List.of(
                    field("name"),
                    field("breed"),
                    field("age", "number"),
                    field("price", "decimal"),
                    field("description"),
                    field("imageUrl"),
                    field("categoryId", "number")
            );
            case "POST /api/v1/services/grooming", "POST /api/v1/services/vaccinations" -> List.of(
                    field("name"),
                    field("description"),
                    field("price", "decimal"),
                    field("available", "boolean")
            );
            case "POST /api/v1/transactions/orders" -> List.of(
                    field("customerId", "number"),
                    field("petId", "number"),
                    field("amount", "decimal")
            );
            case "PUT /api/v1/transactions/{id}/status" -> List.of(
                    field("status", "status")
            );
            default -> List.of();
        };
    }

    private FormField field(String key) {
        return new FormField(key, toLabel(key), "text", "");
    }

    private FormField field(String key, String type) {
        return new FormField(key, toLabel(key), type, "");
    }

    private String toLabel(String key) {
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace("_", " ");
        if (spaced.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public record ApiResponse(int statusCode, String body, String status) {}

    public record EndpointFormSpec(List<String> pathParams, List<FormField> bodyFields) {}

    public record FormField(String key, String label, String type, String placeholder) {}
}
