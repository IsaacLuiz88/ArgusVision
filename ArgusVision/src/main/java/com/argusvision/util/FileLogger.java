package com.argusvision.util;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileLogger {
	private static final String LOG_DIR = System.getProperty("user.home") + File.separator + "ArgusLogsVision";
	private static final String TXT_FILE_NAME = "argusvision.log";
	private static final String JSON_FILE_NAME = "argusvision_events.json";

	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

	static {
		new File(LOG_DIR).mkdir();

		// Inicializa o arquivo JSON como um array vazio se ele não existir
		File jsonFile = new File(LOG_DIR + "/" + JSON_FILE_NAME);
		if (!jsonFile.exists() || jsonFile.length() == 0) {
			try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(jsonFile)))) {
				out.println("["); // Começa com um colchete para abrir o array
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
     * Registra a mensagem no formato de texto (TXT).
     */
    public static void logTxt(String message) {
        try {
            // true para anexar (append)
            FileWriter fw = new FileWriter(LOG_DIR + "/" + TXT_FILE_NAME, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw);

            String time = TIME_FORMAT.format(new Date());
            out.println("[" + time + "] [" + VisionContext.student + "|" + VisionContext.exam + "|" + VisionContext.session + "] " + message);

            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	/**
	 * Registra o evento no formato JSON, anexando-o ao array no arquivo.
	 * 
	 * @param type       Tipo do evento (Ex: "Rosto", "Movimento")
	 * @param detail     Detalhe do evento (Ex: "Olhando Esquerda", "Detectado")
	 * @param confidence Nível de confiança (pode ser o ID da câmera, no caso: 2)
	 */
	public static void logJson(String type, String detail, int confidence) {
		try {
			// true para anexar (append)
			FileWriter fw = new FileWriter(LOG_DIR + "/" + JSON_FILE_NAME, true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw);

			String timestamp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS").format(new Date());

			// Verifica se o arquivo não está vazio para adicionar a vírgula antes de um
			// novo objeto
			if (new File(LOG_DIR + "/" + JSON_FILE_NAME).length() > 2) {
				// Se o arquivo já contém dados (além de '['), adiciona uma vírgula
				out.println(",");
			}

			// Montagem manual do objeto JSON
			String jsonObject = String.format("  {" +
			        "\"timestamp\": \"%s\", " +
			        "\"student\": \"%s\", " +
			        "\"exam\": \"%s\", " +
			        "\"type\": \"%s\", " +
			        "\"detail\": \"%s\", " +
			        "\"confidence\": %d" +
			    "}",
			    timestamp, 
			    VisionContext.student, 
			    VisionContext.exam,
			    type,
			    detail,
			    confidence);

			out.print(jsonObject);
			out.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * DEVE ser chamado no final do programa para fechar o array JSON.
	 */
	public static void closeJsonLog() {
		try (PrintWriter out = new PrintWriter(
				new BufferedWriter(new FileWriter(LOG_DIR + "/" + JSON_FILE_NAME, true)))) {
			out.println("\n]"); // Fecha o array JSON
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
