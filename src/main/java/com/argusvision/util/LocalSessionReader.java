package com.argusvision.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

// Le o arquivo que o plugin Argus grava (SessionHandoff) com a identidade da
// sessao em andamento nesta maquina. E o mesmo aluno/prova do login feito no
// Eclipse - nao deve ser perguntado de novo aqui.
public class LocalSessionReader {

    private static final Path FILE = Paths.get(
            System.getProperty("user.home"), "ArgusLogs", "current_session.properties");

    public static String readStudent() {
        Properties prop = read();
        return prop != null ? emptyToNull(prop.getProperty("student")) : null;
    }

    public static String readExam() {
        Properties prop = read();
        return prop != null ? emptyToNull(prop.getProperty("exam")) : null;
    }

    private static Properties read() {
        if (!Files.exists(FILE)) {
            return null;
        }
        Properties prop = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            prop.load(in);
            return prop;
        } catch (IOException e) {
            System.err.println("Falha ao ler sessão local do Argus (" + FILE + "): " + e.getMessage());
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
