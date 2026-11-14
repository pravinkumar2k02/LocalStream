package com.localstream.player.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.localstream.player.model.RemoteFile;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP Client communicating with the Android Local Stream Server.
 */
public class StreamApiClient {

    private final HttpClient httpClient;
    private final Gson gson;

    public static class PinAuthenticationException extends Exception {
        public PinAuthenticationException(String message) {
            super(message);
        }
    }

    public static class ServerConnectionException extends Exception {
        public ServerConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public StreamApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.gson = new Gson();
    }

    /**
     * Normalizes the base server URL (e.g., adds http:// if missing, removes trailing slash).
     */
    public String normalizeBaseUrl(String serverAddress) {
        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            return "http://192.168.43.1:8080";
        }
        String url = serverAddress.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Builds the full streaming URL with PIN token included for media player seeking.
     */
    public String buildStreamUrl(String baseUrl, String fileId, String pin) {
        String base = normalizeBaseUrl(baseUrl);
        String encodedPin = URLEncoder.encode(pin != null ? pin.trim() : "", StandardCharsets.UTF_8);
        return base + "/stream/" + fileId + "?pin=" + encodedPin;
    }

    /**
     * Fetches the file list from GET /files endpoint.
     */
    public List<RemoteFile> fetchFiles(String serverUrl, String pin)
            throws PinAuthenticationException, ServerConnectionException {
        String base = normalizeBaseUrl(serverUrl);
        String encodedPin = URLEncoder.encode(pin != null ? pin.trim() : "", StandardCharsets.UTF_8);
        String endpoint = base + "/files?pin=" + encodedPin;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Accept", "application/json")
                .header("X-PIN", pin != null ? pin.trim() : "")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode == 401) {
                throw new PinAuthenticationException("Invalid or missing PIN token! Please check the PIN displayed on your Android app.");
            } else if (statusCode == 200) {
                String responseBody = response.body();
                Type listType = new TypeToken<ArrayList<RemoteFile>>() {}.getType();
                List<RemoteFile> files = gson.fromJson(responseBody, listType);
                return files != null ? files : new ArrayList<>();
            } else {
                throw new ServerConnectionException("Server returned unexpected HTTP status code: " + statusCode + "\nResponse: " + response.body(), null);
            }
        } catch (PinAuthenticationException e) {
            throw e;
        } catch (IOException e) {
            throw new ServerConnectionException("Failed to connect to server at " + base + ".\nMake sure your laptop is connected to the Android device's hotspot or the same Wi-Fi network, and that the streaming server is started in the Android app.\nError: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerConnectionException("Request interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ServerConnectionException("Error communicating with server: " + e.getMessage(), e);
        }
    }
}
