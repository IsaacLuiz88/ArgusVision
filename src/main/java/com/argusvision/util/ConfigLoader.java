package com.argusvision.util;

import java.io.IOException;
import java.io.Reader;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigLoader {
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private static final Properties props = new Properties();

    static {
        // 1) config.properties na pasta de execução (comportamento original)
        load(Paths.get("config.properties"));
        // 2) ~/.argus/config.properties - o MESMO arquivo que o plugin Argus lê, então o
        //    endereço do servidor é configurado uma vez só por máquina e sobrescreve o local.
        load(externalFile());
    }

    private static Path externalFile() {
        String custom = System.getProperty("argus.config");
        return (custom != null && !custom.isBlank())
                ? Paths.get(custom)
                : Paths.get(System.getProperty("user.home"), ".argus", "config.properties");
    }

    private static void load(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(reader);
            props.putAll(p);
        } catch (IOException e) {
            System.err.println("Aviso: não foi possível ler " + file + ": " + e.getMessage());
        }
    }

    public static String getProperty(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static int getCameraIndex() {
        return Integer.parseInt(getProperty("camera.index", "0"));
    }

    // Endereço base do ArgusServer (ex.: https://argus.onrender.com). Aceita também os
    // valores antigos do config.properties, que apontavam direto para um endpoint.
    public static String getServerUrl() {
        String url = getProperty("server.url", "").trim();
        for (String legacy : new String[] {"/api/event", "/api/session/active", "/api/session/start"}) {
            if (url.endsWith(legacy)) {
                url = url.substring(0, url.length() - legacy.length());
            }
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return DEFAULT_SERVER_URL;
        }
        return url;
    }

    // Chave de acesso do servidor (security.clientKey no mesmo ~/.argus/config.properties do plugin).
    public static HttpRequest.Builder withClientKey(HttpRequest.Builder builder) {
        String key = getProperty("security.clientKey", "").trim();
        return key.isEmpty() ? builder : builder.header("X-Argus-Key", key);
    }

    public static String getEventUrl() {
        return getServerUrl() + "/api/event";
    }

    public static String getActiveSessionUrl(String encodedStudent) {
        return getServerUrl() + "/api/session/active/" + encodedStudent;
    }
}
