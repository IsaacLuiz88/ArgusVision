package com.argusvision.net;

import com.argusvision.model.Session;
import java.net.http.*;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SessionClient {

    private static final String URL =
        "http://localhost:8080/api/session/start";

    public static Session start(String student, String exam) {

    	String json = String.format(
    	        "{\"student\":\"%s\",\"exam\":\"%s\"}",
    	        student, exam
    	    );

    	try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();

            // Validação do status HTTP
            if (status != 200) {
                throw new RuntimeException(
                    "Servidor respondeu com status " + status + " ao tentar obter sessão ativa"
                );
            }

            // Validação do conteúdo (body não vazio)
            if (body == null || body.isBlank()) {
                throw new RuntimeException(
                    "Servidor não retornou sessão ativa (body vazio)"
                );
            }

            // Desserializa o JSON apenas se houver conteúdo
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response.body(), Session.class);

        } catch (Exception e) {
            throw new RuntimeException(
                "Não foi possível obter sessão ativa do servidor", e
            );
        }
    }

    public static Session fetchByStudent(String student) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                    "http://localhost:8080/api/session/active/" + student
                ))
                .GET()
                .build();

            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new RuntimeException("Aluno não possui sessão ativa.");
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response.body(), Session.class);

        } catch (Exception e) {
            throw new RuntimeException(
                "Não foi possível obter sessão ativa do aluno", e
            );
        }
    }

    public static Session fetch() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/session/active"))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new RuntimeException("Nenhuma sessão ativa encontrada.");
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response.body(), Session.class);

        } catch (Exception e) {
            throw new RuntimeException("Não foi possível obter sessão ativa do servidor", e);
        }
    }
}
