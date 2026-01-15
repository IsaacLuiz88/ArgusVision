package com.argusvision.net;

import com.argusvision.model.Session;
import java.net.http.*;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SessionClient {

    private static final String URL =
        "http://localhost:8080/api/session/active";

    public static Session fetch() {

    	try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .GET()
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
}
