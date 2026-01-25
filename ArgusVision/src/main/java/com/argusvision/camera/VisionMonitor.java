package com.argusvision.camera;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import com.argusvision.util.FileLogger;
import com.argusvision.util.VisionContext;
import com.argusvision.util.VisionEventSender;

import java.awt.Color;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VisionMonitor {

	private static final int CAMERA_INDEX = 0;
	private static final Size FRAME_SIZE = new Size(640, 480);
	/** Intervalo fixo de envio de frame (1 segundo) */
	private static final long FRAME_SEND_INTERVAL_SEC = 2;

	/** Intervalo mínimo entre eventos de rosto */
	private static final long FACE_SEND_INTERVAL_MS = 2000;

	private final CascadeClassifier faceDetector;
	private final VisionOutput output;
	private final VisionEventSender eventSender;

	private final String studentName;
	private final String examName;

	private volatile boolean running;

	private String lastFaceStatus = "";
	private long lastFaceSentTime = 0;

	/** Scheduler exclusivo para envio de frames */
	private ScheduledExecutorService frameScheduler;

	/** Último frame capturado (sempre sobrescrito) */
	private volatile Mat lastFrame;

	private String pendingFaceStatus = "";
	private long pendingSince = 0;

	private static final long FACE_STABLE_TIME_MS = 700; // tempo mínimo estável

//    private final BackgroundSubtractorMOG2 motionDetector;
//    private boolean motionDetected = false;

//    private long lastFaceSentTime = 0;
//   private static final long FACE_SEND_INTERVAL = 1000; // 1 segundo

	public VisionMonitor(String student, String exam, String session, VisionOutput output) {
		this.studentName = student;
		this.examName = exam;
		this.output = output;

		VisionContext.init(student, exam, session);

		this.eventSender = new VisionEventSender();
		this.faceDetector = loadCascade("facedetector/lbpcascade_frontalface_improved.xml");
		// this.motionDetector = Video.createBackgroundSubtractorMOG2();

		//gui.setIdentity(student, exam);
		output.addLog("LBP Cascade carregado com sucesso");
		output.addLog("Vision iniciado para " + student + " | " + exam);
		FileLogger.logTxt("[VISION] Inicializado: " + student + " | " + exam + " | " + session);
		FileLogger.logJson("vision", "INIT", 0);
	}

	/**
	 * Carrega o classificador Haar/LBP a partir dos recursos do projeto.
	 */
	private CascadeClassifier loadCascade(String path) {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
			if (is == null)
				throw new RuntimeException("Cascade não encontrado: " + path);

			Path temp = Files.createTempFile("lbp-", ".xml");
			Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);

			CascadeClassifier cc = new CascadeClassifier(temp.toString());
			if (cc.empty())
				throw new RuntimeException("Cascade inválido");

			return cc;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Inicia o monitoramento da câmera e o scheduler de envio de frames.
	 */
	public void start() {
		running = true;

		startFrameScheduler();
		startVisionLoop();
	}

	/**
	 * Loop principal de captura e processamento de visão. NÃO faz envio de rede.
	 */
	private void startVisionLoop() {
		new Thread(() -> {
			VideoCapture camera = new VideoCapture(CAMERA_INDEX);

			if (!camera.isOpened()) {
				output.setStatus("Sem Webcam");
				eventSender.sendEventAsync("vision", "SEM_WEBCAM");
				FileLogger.logTxt("[VISION] Webcam não disponível");
				FileLogger.logJson("vision", "SEM_WEBCAM", 1);
				return;
			}

			output.setStatus("Monitorando");

			Mat frame = new Mat();
			Mat resized = new Mat();
			Mat gray = new Mat();

			while (running) {
				if (!camera.read(frame))
					continue;

				Imgproc.resize(frame, resized, FRAME_SIZE);
				Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY);

				detectFace(resized, gray);
				// detectMotion(resized, fgMask);
				// sendFrame(resized);
				lastFrame = resized.clone(); // sempre sobrescreve
				output.updateFrame(resized);

				sleep(33);
			}
			camera.release();
			output.setStatus("Encerrado");
		}, "VisionLoop").start();
	}

	/**
	 * Scheduler responsável por enviar frames em intervalo fixo.
	 */
	private void startFrameScheduler() {
		frameScheduler = Executors.newSingleThreadScheduledExecutor();

		frameScheduler.scheduleAtFixedRate(() -> {
			if (lastFrame == null)
				return;

			String base64 = FrameEncoder.encodeToBase64(lastFrame);
			eventSender.updateVisionFrame(studentName, examName, base64);
			eventSender.flushLatestFrame();

		}, 0, FRAME_SEND_INTERVAL_SEC, TimeUnit.SECONDS);
	}

	/**
	 * Detecção e classificação de posição do rosto. Eventos são enviados apenas
	 * quando: - o status muda - ou o intervalo mínimo expira
	 */
	private void detectFace(Mat frame, Mat gray) {
		MatOfRect faces = new MatOfRect();
		faceDetector.detectMultiScale(gray, faces, 1.1, 3, 0, new Size(80, 80), new Size());

		Rect[] detected = faces.toArray();
		String status;
		Color color;

		if (detected.length == 0) {
			status = "SEM_ROSTO";
			color = Color.RED;
		} else {
			Rect face = detected[0];
			int cx = face.x + face.width / 2;
			int cy = face.y + face.height / 2;

			double w = frame.width();
			double h = frame.height();

			if (cx < w * 0.30)
				status = "ROSTO_ESQUERDA";
			else if (cx > w * 0.70)
				status = "ROSTO_DIREITA";
			else if (cy < h * 0.30)
				status = "ROSTO_CIMA";
			else if (cy > h * 0.70)
				status = "ROSTO_BAIXO";
			else
				status = "ROSTO_CENTRO";

			color = new Color(0, 150, 0);
			Imgproc.rectangle(frame, face, new Scalar(color.getBlue(), color.getGreen(), color.getRed()), 2);
		}

		long now = System.currentTimeMillis();

		/*
		 * Se o status mudou, começamos a contar o tempo de estabilidade
		 */
		if (!status.equals(pendingFaceStatus)) {
		    pendingFaceStatus = status;
		    pendingSince = now;
		    return;
		}

		/*
		 * Se o status ainda não ficou estável tempo suficiente, ignoramos
		 */
		if (now - pendingSince < FACE_STABLE_TIME_MS) {
		    return;
		}


		if (!status.equals(lastFaceStatus) && now - lastFaceSentTime >= FACE_SEND_INTERVAL_MS) {
			eventSender.sendEventAsync("vision", status);

			output.updateFaceStatus(status, color);
			output.addLog("[" + studentName + "] Rosto: " + status);

			FileLogger.logTxt("[Rosto] " + status);
			FileLogger.logJson("Rosto", status, 2);

			lastFaceStatus = status;
			lastFaceSentTime = now;
		}
	}

	public void stop() {
		running = false;

		if (frameScheduler != null) {
			frameScheduler.shutdownNow();
		}

		eventSender.sendEventAsync("vision", "VISION_ENCERRADO");
		FileLogger.logTxt("[VISION] Encerrado");
		FileLogger.logJson("vision", "STOPPED", 0);
		eventSender.shutdown();
	}

	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
		}
	}
}
