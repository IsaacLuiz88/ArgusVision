package main.java.com.argusvision;

import org.opencv.core.*;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;

import main.java.com.argusvision.util.FileLogger;
import main.java.com.argusvision.util.VisionContext;
import main.java.com.argusvision.util.VisionEventSender;

import org.opencv.imgproc.Imgproc;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class VisionMonitor {

	private CascadeClassifier frontalDetector;
	private CascadeClassifier profileDetector;
	private BackgroundSubtractorMOG2 motionDetector;
	private String studentName;
	private String examName;
	private CameraViewer gui;

	private String lastFaceStatus = "";
	private boolean lastMotionStatus = false;
	private boolean running = false;

	// Variáveis de controle para o anti-flicker
	private int noFaceCount = 0;
	private final int MAX_NO_FACE_FRAMES = 5;

	// Variáveis de controle de imagem
	private Mat previousFrame;
	private Mat foregroundMask;

	// Parâmetros de detecção (Ajustados para melhor estabilidade)
	private final double FRONTAL_SCALE_FACTOR = 1.12;
	private final int FRONTAL_MIN_NEIGHBORS = 3;
	private final double PROFILE_SCALE_FACTOR = 1.12; // Mais agressivo, detecta rostos menores
	private final int PROFILE_MIN_NEIGHBORS = 2; // Menos vizinhos para detectar mais perfis

	private long lastGuiUpdate = 0;

	private VisionEventSender eventSender;

	public VisionMonitor(String studentName, String examName, CameraViewer gui) {
		this.studentName = studentName;
		this.examName = examName;
		VisionContext.student = studentName;
		VisionContext.exam = examName;

		this.gui = gui;

		// 1. Inicializa detectores
		this.frontalDetector = new CascadeClassifier("haarcascade_frontalface_default.xml");
//		this.profileDetector = new CascadeClassifier("haarcascade_profileface.xml");

		// 2. Verifica se os arquivos foram carregados
		if (frontalDetector.empty()) {
			gui.addLog("ERRO: HaarCascadeFrontalFace (default) não encontrado. Verifique o caminho.");
			System.err.println("Erro ao carregar xml frontal.");
		}
//		if (profileDetector.empty()) {
//			gui.addLog("AVISO: HaarCascadeProfile (perfil) não encontrado. Detecção de perfil desativada.");
//			System.err.println("Aviso: Erro ao carregar xml perfil.");
//		}

		this.motionDetector = Video.createBackgroundSubtractorMOG2();
		this.motionDetector.setVarThreshold(25);
		this.foregroundMask = new Mat();
		this.eventSender = new VisionEventSender();
	}

	public void start() {
		running = true;

		// Thread dedicada para processamento de vídeo
		new Thread(() -> {
			VideoCapture camera = new VideoCapture(0);

			if (!camera.isOpened()) {
				gui.addLog("ERRO FATAL: Câmera não detectada!");
				return;
			} else {
				gui.addLog("Câmera iniciada com sucesso.");
				gui.setStatus("Monitoramento Ativo");
			}

			Mat frame = new Mat();
			Mat displayFrame = new Mat(); // Frame para exibir na GUI

			while (running && camera.isOpened()) {
				if (camera.read(frame)) {

					// Redimensionar para 640x480
					Imgproc.resize(frame, displayFrame, new Size(640, 480));

					// --- 1. Detectar Rostos (Lógica Aprimorada) ---
					detectFaces(displayFrame);

					// --- 2. Detectar Movimento ---
					detectMotion(displayFrame);

					// --- 3. Atualizar GUI ---
					// gui.updateFrame(displayFrame);
					long now = System.currentTimeMillis();
					if (now - lastGuiUpdate > 40) { // antes estava 80 -> ~12 FPS para GUI
						gui.updateFrame(displayFrame);
						lastGuiUpdate = now;
					}

				} else {
					gui.addLog("Falha ao ler frame da câmera.");
				}

				// Controle de FPS (~30fps)
				try {
					Thread.sleep(33);
				} catch (InterruptedException e) {
				}
			}

			camera.release();
			gui.setStatus("Câmera Encerrada");
		}).start();
	}

	public void stop() {
		running = false;
	}

	public void release() {
		if (foregroundMask != null)
			foregroundMask.release();

		if (eventSender != null)
			eventSender.shutdown(); // DESLIGA a thread HTTP
	}

	private void detectFaces(Mat frame) {
		MatOfRect faces = new MatOfRect();
		Mat gray = new Mat();

		Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
		Imgproc.equalizeHist(gray, gray);

		List<Rect> allFaces = new ArrayList<>();

		// =========================================================
		// === 1. DETECÇÃO FRONTAL =================================
		// =========================================================
		MatOfRect frontalFaces = new MatOfRect();
		frontalDetector.detectMultiScale(gray, frontalFaces, FRONTAL_SCALE_FACTOR, FRONTAL_MIN_NEIGHBORS, 0,
				new Size(60, 60), new Size(400, 400));
		allFaces.addAll(frontalFaces.toList());

		// =========================================================
		// === 2. DETECÇÃO DE PERFIL (Se o detector estiver carregado) =
		// =========================================================
//		if (!profileDetector.empty()) {
//			MatOfRect profileFaces = new MatOfRect();
//
//			// Tenta Perfil 1: Lado normal
//			profileDetector.detectMultiScale(gray, profileFaces, PROFILE_SCALE_FACTOR, PROFILE_MIN_NEIGHBORS, 0,
//					new Size(60, 60), new Size(400, 400));
//			allFaces.addAll(profileFaces.toList());
//
//			// Tenta Perfil 2: Imagem espelhada
//			Mat flippedGray = new Mat();
//			Core.flip(gray, flippedGray, 1);
//			profileDetector.detectMultiScale(flippedGray, profileFaces, PROFILE_SCALE_FACTOR, PROFILE_MIN_NEIGHBORS, 0,
//					new Size(60, 60), new Size(400, 400));
//			flippedGray.release();
//
//			// Inverter as coordenadas X dos retângulos de volta
//			int frameWidth = frame.width();
//			for (Rect face : profileFaces.toList()) {
//				face.x = frameWidth - face.x - face.width;
//				allFaces.add(face);
//			}
//		}

		// =========================================================
		// === 3. SELECIONAR O MAIOR ROSTO E FILTRAR RUÍDO =========
		// =========================================================
		Rect mainFace = null;
		double maxArea = 0;

		// Encontra o maior retângulo de rosto na lista unificada
		for (Rect face : allFaces) {
			double area = face.area();
			if (area > maxArea) {
				maxArea = area;
				mainFace = face;
			}
		}

		// Filtragem final: O maior objeto detectado precisa ser grande o suficiente
		if (maxArea < 2500) { // Limite de área mínima (ajuste conforme a resolução e distância)
			mainFace = null; // Ignora se for muito pequeno (provavelmente ruído/falso positivo)
		}

		// Aplicar NMS Simples para evitar duplicação (fake duplicates)
		// Se houver um rosto principal grande, vamos filtrar quaisquer retângulos
		// que se sobreponham a ele e tenham área significativamente menor (ruído).
		List<Rect> finalFaces = new ArrayList<>();
		finalFaces.add(mainFace);

		for (Rect face : allFaces) {
			if (face != mainFace) {
				// Se a sobreposição for alta E a área for pequena (falso positivo), ignore.
				if (isOverlap(mainFace, face) && face.area() < mainFace.area() * 0.7) {
					// Ignorar este retângulo, é provavelmente ruído
				} else {
					// Este é um segundo rosto não sobreposto (ou muito grande para ser ruído)
					// Nota: Para este projeto, que espera apenas um aluno, podemos focar apenas no
					// mainFace.
					// Para simplificar e garantir que apenas UM rosto seja rastreado:
					// Não adicionamos outros retângulos aqui.
				}
			}
		}

		// Mantemos apenas o mainFace para o rastreamento final
		allFaces.clear();
		allFaces.add(mainFace);

//		if (mainFace != null) {
//			List<Rect> filtrados = new ArrayList<>();
//
//			for (Rect r : allFaces) {
//				if (!isOverlap(mainFace, r))
//					continue;
//				filtrados.add(r);
//			}
//
//			allFaces.clear();
//			allFaces.add(mainFace); // mantém só o principal
//		}

		// Ignorar rostos muito próximos (fake duplicates)
		if (mainFace != null) {
			for (Rect f : allFaces) {
				if (f != mainFace && similarSizeAndPosition(mainFace, f)) {
					// Se for similar, ignora os outros
				}
			}
		}

		// =========================================================
		// === 4. ATUALIZAR STATUS E ANTI-FLICKER ==================
		// =========================================================
		String currentStatus = "Nenhum Rosto";
		Color currentColor = Color.RED;

		if (mainFace != null) {
			// Rosto encontrado: resetar contador
			noFaceCount = 0;

			int centerX = mainFace.x + mainFace.width / 2;
			int centerY = mainFace.y + mainFace.height / 2;

			// Definição das zonas de desvio (Margens em percentual do frame)
			double horizontalThreshold = 0.30;
			double verticalThreshold = 0.35;

			// Detecção de perfil tem prioridade máxima em ângulos extremos
			if (isProfileDetection(mainFace)) {
				currentStatus = "Detectado (Perfil Lateral)";
				currentColor = Color.ORANGE;
			}
			// Lógica de direção frontal (usando a correção de espelhamento)
			else if (centerX < frame.width() * horizontalThreshold) {
				currentStatus = "Olhando Direita";
				currentColor = Color.ORANGE;
			} else if (centerX > frame.width() * (1.0 - horizontalThreshold)) {
				currentStatus = "Olhando Esquerda";
				currentColor = Color.ORANGE;
			} else if (centerY < frame.height() * verticalThreshold) {
				currentStatus = "Olhando Para Cima";
				currentColor = Color.ORANGE;
			} else if (centerY > frame.height() * (1.0 - verticalThreshold)) {
				currentStatus = "Olhando Para Baixo";
				currentColor = Color.ORANGE;
			} else {
				currentStatus = "Detectado (Centro)";
				currentColor = new Color(0, 150, 0); // Verde
			}

			// Desenhar retângulo e status
			Imgproc.rectangle(frame, mainFace,
					new Scalar(currentColor.getBlue(), currentColor.getGreen(), currentColor.getRed()), 2);
			Imgproc.putText(frame, studentName, new Point(mainFace.x, mainFace.y - 10), Imgproc.FONT_HERSHEY_SIMPLEX,
					0.7, new Scalar(currentColor.getBlue(), currentColor.getGreen(), currentColor.getRed()), 2);

			gui.updateFaceStatus(currentStatus, currentColor);

			if (!currentStatus.equals(lastFaceStatus)) {
				// Formata a ação para o servidor: Ex: "Detectado (Centro)" ->
				// "DETECTADO_CENTRO"
				String action = currentStatus.replaceAll(" ", "_").replaceAll("[()]", "").toUpperCase();

				// === ENVIAR PARA O SERVIDOR ===
				eventSender.sendEventAsync("vision", action);

				// Logging TXT e JSON
				String logMessage = String.format("[%s | %s] Rosto: %s (SENT)", studentName, examName, currentStatus);
				gui.addLog(logMessage); // Log na GUI
				FileLogger.logTxt(logMessage); // Log no arquivo TXT
				FileLogger.logJson("Rosto", currentStatus, 2); // Log no arquivo JSON
				lastFaceStatus = currentStatus; // 4. Atualiza o status
			}
		} else {
			// Rosto não encontrado: incrementar contador
			noFaceCount++;

			if (noFaceCount >= MAX_NO_FACE_FRAMES) {
				if (!"Nenhum Rosto".equals(lastFaceStatus)) {
					String action = "NO_FACE_LONG"; // Ação para o servidor classificar como RED

					// === ENVIAR PARA O SERVIDOR ===
					eventSender.sendEventAsync("vision", action);

					// Log Local
					String logMessage = String.format("[%s | %s] Rosto: %s (SENT)", studentName, examName,
							currentStatus);
					gui.addLog(logMessage);
					FileLogger.logTxt(logMessage);
					FileLogger.logJson("Rosto", "Nenhum Rosto", 2); // Log JSON da perda

					lastFaceStatus = "Nenhum Rosto";
				}
				// Perda confirmada: atualiza a GUI

				gui.updateFaceStatus("Nenhum Rosto", Color.RED);
				// Desenhar aviso na tela
				Imgproc.putText(frame, "ROSTO NAO DETECTADO", new Point(50, 50), Imgproc.FONT_HERSHEY_SIMPLEX, 1,
						new Scalar(0, 0, 255), 2);
			}
			// Se estiver em modo flicker, o status da GUI permanece o anterior, mas não
			// desenhamos nada.
		}
		gray.release();
	}

	private boolean isOverlap(Rect a, Rect b) {
		Rect inter = new Rect();
		if (!intersect(a, b, inter))
			return false;

		double areaInter = inter.area();
		double areaSmall = Math.min(a.area(), b.area());

		return areaInter / areaSmall > 0.6;
	}

	private boolean intersect(Rect a, Rect b, Rect out) {
		int x1 = Math.max(a.x, b.x);
		int y1 = Math.max(a.y, b.y);
		int x2 = Math.min(a.x + a.width, b.x + b.width);
		int y2 = Math.min(a.y + a.height, b.y + b.height);

		if (x2 <= x1 || y2 <= y1)
			return false;

		out.x = x1;
		out.y = y1;
		out.width = x2 - x1;
		out.height = y2 - y1;
		return true;
	}

	private boolean similarSizeAndPosition(Rect r1, Rect r2) {

		double areaDiff = Math.abs(r1.area() - r2.area()) / r1.area();

		double dx = Math.abs(r1.x - r2.x);
		double dy = Math.abs(r1.y - r2.y);

		return areaDiff < 0.3 && dx < 40 && dy < 40;
	}

	/**
	 * Helper: Verifica se o retângulo principal é mais provável de ser um perfil.
	 * Simplificamos para priorizar o status de perfil se o detector frontal falhou,
	 * mas ele foi capturado pela lógica de perfil (que já está unificada em
	 * allFaces).
	 */
//	private boolean isProfileDetection(Rect mainFace, List<Rect> frontalFaces, int frameWidth) {
//		// Se o rosto principal for detectado e o detector frontal não encontrou nada,
//		// é uma boa indicação de que o detector de perfil foi o responsável.
//		// No entanto, é mais seguro verificar a proporção (largura/altura) do
//		// retângulo.
//
//		double aspectRatio = (double) mainFace.width / mainFace.height;
//		// Um rosto de perfil é mais estreito que um rosto frontal (aspect ratio menor).
//		// Um rosto frontal é tipicamente 0.7 - 0.9. Um perfil será menor, ex: < 0.65.
//
//		if (aspectRatio < 0.65) {
//			return true;
//		}
//
//		return false;
//	}
	private boolean isProfileDetection(Rect face) {
		double aspectRatio = (double) face.width / face.height;
		return aspectRatio < 0.55; // mais restritivo = menos falso-positivo
	}

	// O método detectMotion permanece o mesmo
	private void detectMotion(Mat frame) {
		// ... (código inalterado)
		motionDetector.apply(frame, foregroundMask);

		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierarchy = new Mat();

		// Limpar ruído
		Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
		Imgproc.morphologyEx(foregroundMask, foregroundMask, Imgproc.MORPH_OPEN, kernel);

		Imgproc.findContours(foregroundMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

		boolean motionDetected = false;

		for (MatOfPoint contour : contours) {
			if (Imgproc.contourArea(contour) > 1000) {
				motionDetected = true;
				Rect rect = Imgproc.boundingRect(contour);
				// Desenhar área de movimento em Vermelho
				Imgproc.rectangle(frame, rect, new Scalar(0, 0, 255), 1);
			}
		}
//		if (motionDetected) {
//			gui.updateMotionStatus("Detectado", Color.RED);
//		} else {
//			gui.updateMotionStatus("Estável", new Color(0, 150, 0));
//		}
		if (motionDetected != lastMotionStatus) {

			String msg = motionDetected ? "Movimento detectado" : "Movimento cessado";
			String detail = motionDetected ? "MOTION_DETECTED" : "MOTION_CEASED";
			String type = "vision";

			// === ENVIAR PARA O SERVIDOR ===
			eventSender.sendEventAsync(type, detail);

			// Log na GUI
			gui.addLog("[" + studentName + " | " + examName + "] " + msg + " (SENT)");

			// Log no arquivo TXT
			FileLogger.logTxt("[" + studentName + " | " + examName + "] " + msg);

			// Log no arquivo JSON
			FileLogger.logJson(type, detail, 2);

			lastMotionStatus = motionDetected;
		}
		gui.updateMotionStatus(motionDetected ? "Detectado" : "Estável",
				motionDetected ? Color.RED : new Color(0, 150, 0));
	}
}