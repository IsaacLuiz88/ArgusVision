package com.argusvision.util;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

public class FileLogger {
	private static final String LOG_DIR = System.getProperty("user.home") + File.separator + "ArgusLogsVision";
	// Formatador para o sufixo da data no nome do arquivo (ex: 16042026)
    private static final DateTimeFormatter DATE_FILE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy").withZone(java.time.ZoneId.of("America/Sao_Paulo"));;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    static {
    	TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
    }

	static {
        File dir = new File(LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

	/**
     * Gera o nome do arquivo seguindo o padrão: Aluno_Prova_Data.extensão
     */
    private static String generateFileName(String extension) {
        String student = VisionContext.student != null ? VisionContext.student : "Desconhecido";
        String exam = VisionContext.exam != null ? VisionContext.exam : "SemProva";
        String date = LocalDateTime.now().format(DATE_FILE_FORMATTER);
        
        // Remove espaços ou caracteres especiais que podem dar erro no Windows/Linux
        String sanitized = (student + "_" + exam + "_" + date).replaceAll("[^a-zA-Z0-9_]", "");
        
        return sanitized + extension;
    }

	/**
     * Registra a mensagem no formato de texto (TXT).
     */
    public static void logTxt(String message) {
        // Agora o nome do arquivo é dinâmico
        String fileName = generateFileName(".log");
        File logFile = new File(LOG_DIR + File.separator + fileName);

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
            String time = TIME_FORMAT.format(new Date());
            out.println("[" + time + "] [" + VisionContext.student + "|" + VisionContext.exam + "|" + VisionContext.session + "] " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	/**
	 * * Registra o evento no formato JSON Lines (.jsonl).
     * Cada linha é um JSON independente. Não usa vírgulas entre linhas nem colchetes.
	 * 
	 * @param type       Tipo do evento (Ex: "Rosto", "Movimento")
	 * @param detail     Detalhe do evento (Ex: "Olhando Esquerda", "Detectado")
	 * @param confidence Nível de confiança (pode ser o ID da câmera, no caso: 2)
	 */
    public static void logJson(String type, String detail, int confidence) {
        // Agora o nome do arquivo é dinâmico
        String fileName = generateFileName(".jsonl");
        File jsonFile = new File(LOG_DIR + File.separator + fileName);
        
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(jsonFile, true)))) {
        	SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("America/Sao_Paulo")); // <--- ADICIONE ISSO
            String timestamp = sdf.format(new Date());

            String jsonObject = String.format(
                "{\"timestamp\":\"%s\",\"student\":\"%s\",\"exam\":\"%s\",\"type\":\"%s\",\"detail\":\"%s\",\"confidence\":%d}",
                timestamp, 
                VisionContext.student, 
                VisionContext.exam,
                type,
                detail,
                confidence
            );

            out.println(jsonObject);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
