package social;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProviderHttpClient {

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public Uni<JsonNode> postForm(String url, Map<String, String> form, Map<String, String> headers) {
        return Uni.createFrom().item(() -> {
            try {
                String body = form.entrySet().stream()
                        .filter(entry -> entry.getValue() != null)
                        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                        .collect(Collectors.joining("&"));

                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                headers.forEach(builder::header);
                return send(builder.build());
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException("Provider form request failed", e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    public Uni<JsonNode> postJson(String url, Object body, Map<String, String> headers) {
        return Uni.createFrom().item(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
                headers.forEach(builder::header);
                return send(builder.build());
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException("Provider JSON request failed", e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    public Uni<JsonNode> get(String url, Map<String, String> headers) {
        return Uni.createFrom().item(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET();
                headers.forEach(builder::header);
                return send(builder.build());
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException("Provider GET request failed", e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Provider request failed with status " + status + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
