package com.argusvision.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class VisionEventSender {

	private static final String SERVER_URL = "http://localhost:8080/api/event";
	
	/** Executor para eventos simples (teclado, foco, rosto, etc) */
    private final ExecutorService eventExecutor;

    /** Executor dedicado para envio de frames */
    private final ExecutorService frameExecutor;

    /** Armazena sempre o frame mais recente */
    private final AtomicReference<String> latestFrameBase64;

	private final HttpClient httpClient;

	public VisionEventSender() {
		this.eventExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Vision-Event-Sender"));
        this.frameExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Vision-Frame-Sender"));
        this.latestFrameBase64 = new AtomicReference<>(null);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
	}

	/**
     * Envia eventos simples (sem imagem).
     * Uso: foco, estado, rosto, movimento etc.
     */
    public void sendEventAsync(String type, String action) {
        eventExecutor.submit(() -> {
            String json = String.format(
        		"{\"type\":\"%s\",\"action\":\"%s\",\"timestamp\":%d," + "\"student\":\"%s\",\"exam\":\"%s\",\"session\":\"%s\"}",
                type, action, System.currentTimeMillis(), VisionContext.student, VisionContext.exam
            );

            sendToServer(json);
        });
    }

    /**
     * Atualiza o frame mais recente.
     * 
     * IMPORTANTE:
     * - Não envia imediatamente
     * - Substitui qualquer frame antigo ainda não enviado
     */
    public void updateVisionFrame(String student, String exam, String base64Image) {
        latestFrameBase64.set(
            buildFrameJson(student, exam, base64Image)
        );
    }

    /**
     * Envia o frame mais recente, se existir.
     * 
     * Deve ser chamado por um scheduler externo (ex: a cada 1 segundo).
     */
    public void flushLatestFrame() {
        frameExecutor.submit(() -> {
            String json = latestFrameBase64.getAndSet(null);
            if (json != null) {
                sendToServer(json);
            }
        });
    }

    /**
     * Monta o JSON do frame de forma controlada.
     */
    private String buildFrameJson(String student, String exam, String base64Image) {
        return String.format(
			"{\"type\":\"vision_frame\",\"student\":\"%s\",\"exam\":\"%s\",\"session\":\"%s\"," +
				    "\"timestamp\":%d,\"image\":\"%s\"}",
				    VisionContext.student,
				    VisionContext.exam,
				    VisionContext.session,
				    System.currentTimeMillis(),
				    base64Image.replace("\"", "\\\"")
        );
    }

    /**
     * Envia efetivamente o JSON para o servidor.
     * 
     * Método síncrono por design:
     * - Evita async duplicado
     * - Permite controle real de fila
     */
    private void sendToServer(String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVER_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("[ArgusVision] HTTP " + response.statusCode());

        } catch (Exception e) {
            System.err.println("[ArgusVision] Erro ao enviar evento: " + e.getMessage());
        }
    }

    /**
     * Finaliza corretamente os executores.
     */
    public void shutdown() {
        eventExecutor.shutdownNow();
        frameExecutor.shutdownNow();
    }
}