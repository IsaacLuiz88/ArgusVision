package com.argusvision.net;

import com.argusvision.model.Session;
import com.argusvision.util.ConfigLoader;
import java.net.http.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SessionClient {

    /**
     * Busca a sessão ativa de um aluno específico. Único método usado pelo
     * ArgusVision — sempre identifique o aluno pelo nome, nunca por "primeira
     * sessão ativa do sistema" (isso quebra com múltiplos alunos simultâneos).
     */
    public static Session fetchByStudent(String student) {
        try {
            // URLEncoder produz codificação de query string (espaço -> "+");
            // um segmento de path precisa de espaço -> "%20".
            String encodedStudent = URLEncoder.encode(student, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ConfigLoader.getActiveSessionUrl(encodedStudent)))
                .GET()
                .build();

            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new RuntimeException("Aluno não possui sessão ativa.");
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response.body(), Session.class);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "Não foi possível obter sessão ativa do aluno", e
            );
        }
    }
}
