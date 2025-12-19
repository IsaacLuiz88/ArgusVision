package com.argusvision.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VisionEventSender {

    private static final String SERVER_URL = "http://localhost:8080/api/event";
    private final ExecutorService executor;
    private final HttpClient httpClient;

    public VisionEventSender() {
        this.executor = Executors.newSingleThreadExecutor();
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Envia um evento simplificado para o servidor (sem Base64).
     */
    public void sendEventAsync(String type, String action) {

        // Cria o JSON SIMPLIFICADO (sem o campo 'image')
        String json = String.format(
            "{\"type\":\"%s\",\"action\":\"%s\",\"time\":%d,\"student\":\"%s\",\"exam\":\"%s\"}",
            type, action, System.currentTimeMillis(), VisionContext.student, VisionContext.exam
        );

        executor.submit(() -> sendToServer(json));
    }

    private void sendToServer(String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        System.out.println("[ArgusVision] Servidor respondeu: " + response.statusCode());
                    })
                    .exceptionally(ex -> {
                        System.err.println("[ArgusVision] Erro na comunicação com o servidor: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception ex) {
            System.err.println("[ArgusVision] Falha ao criar requisição: " + ex.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}