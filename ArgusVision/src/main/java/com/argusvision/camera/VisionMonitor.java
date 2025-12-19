package com.argusvision.camera;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;

import com.argusvision.util.FileLogger;
import com.argusvision.util.VisionContext;
import com.argusvision.util.VisionEventSender;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * VisionMonitor: Monitora um aluno durante um exame.
 * - Detecta rosto frontal.
 * - Detecta movimento no vídeo.
 * - Atualiza GUI e envia eventos para servidor.
 */
public class VisionMonitor {

    private CascadeClassifier frontalDetector;      // Detector de rosto frontal
    private BackgroundSubtractorMOG2 motionDetector; // Detector de movimento
    private CameraViewer gui;

    private String studentName;
    private String examName;

    private String lastFaceStatus = "";
    private boolean lastMotionStatus = false;
    private boolean running = false;

    // Controle anti-flicker (para evitar status piscando)
    private int noFaceCount = 0;
    private final int MAX_NO_FACE_FRAMES = 5;

    // Máscara usada para detecção de movimento
    private Mat foregroundMask;

    // Parâmetros de detecção de rosto
    private final double FRONTAL_SCALE_FACTOR = 1.12;
    private final int FRONTAL_MIN_NEIGHBORS = 3;

    private long lastGuiUpdate = 0;

    private VisionEventSender eventSender;

    public VisionMonitor(String studentName, String examName, CameraViewer gui) {
        this.studentName = studentName;
        this.examName = examName;
        VisionContext.student = studentName;
        VisionContext.exam = examName;

        this.gui = gui;

        // Inicializa detector de rosto frontal
        this.frontalDetector = loadCascade("facedetector/haarcascade_frontalface_default.xml");
        gui.addLog("HaarCascade frontal carregado com sucesso.");
        if (frontalDetector.empty()) {
            gui.addLog("ERRO: HaarCascadeFrontalFace (default) não encontrado. Verifique o caminho.");
            System.err.println("Erro ao carregar xml frontal.");
        }

        // Inicializa detector de movimento
        this.motionDetector = Video.createBackgroundSubtractorMOG2();
        this.motionDetector.setVarThreshold(25);
        this.foregroundMask = new Mat();

        // Inicializa envio de eventos
        this.eventSender = new VisionEventSender();
    }

    private CascadeClassifier loadCascade(String resourcePath) {
        try (InputStream is =
                 getClass().getClassLoader().getResourceAsStream(resourcePath)) {

            if (is == null) {
                throw new RuntimeException(
                    "HaarCascade não encontrado no classpath: " + resourcePath
                );
            }

            Path tempFile = Files.createTempFile("cascade-", ".xml");
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);

            CascadeClassifier classifier =
                new CascadeClassifier(tempFile.toAbsolutePath().toString());

            if (classifier.empty()) {
                throw new RuntimeException("CascadeClassifier vazio após carregamento");
            }

            return classifier;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar Haar Cascade", e);
        }
    }

    /** Inicia o monitoramento em thread separada */
    public void start() {
        running = true;

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
            Mat displayFrame = new Mat(); // Frame para exibição na GUI

            while (running && camera.isOpened()) {
                if (camera.read(frame)) {
                    // Redimensiona para 640x480
                    Imgproc.resize(frame, displayFrame, new Size(640, 480));

                    // Detecta rostos
                    detectFaces(displayFrame);

                    // Detecta movimento
                    detectMotion(displayFrame);

                    // Atualiza GUI (~25 FPS)
                    long now = System.currentTimeMillis();
                    if (now - lastGuiUpdate > 40) {
                        gui.updateFrame(displayFrame);
                        lastGuiUpdate = now;
                    }

                } else {
                    gui.addLog("Falha ao ler frame da câmera.");
                }

                // Pequena pausa para controlar FPS (~30fps)
                try {
                    Thread.sleep(33);
                } catch (InterruptedException ignored) {
                }
            }

            camera.release();
            gui.setStatus("Câmera Encerrada");
        }).start();
    }

    /** Para o monitoramento */
    public void stop() {
        running = false;
    }

    /** Libera recursos */
    public void release() {
        if (foregroundMask != null)
            foregroundMask.release();

        if (eventSender != null)
            eventSender.shutdown();
    }

    /** Detecta o rosto frontal e atualiza status da GUI/Servidor */
    private void detectFaces(Mat frame) {
    	if (frontalDetector == null || frontalDetector.empty()) {
    	    return;
    	}

        MatOfRect faces = new MatOfRect();
        Mat gray = new Mat();

        // Converte para escala de cinza e equaliza histograma
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);

        // Detecta rostos frontais
        frontalDetector.detectMultiScale(gray, faces, FRONTAL_SCALE_FACTOR, FRONTAL_MIN_NEIGHBORS,
                0, new Size(60, 60), new Size(400, 400));

        // Seleciona o maior rosto (desconsidera ruído)
        Rect mainFace = null;
        double maxArea = 0;
        for (Rect face : faces.toList()) {
            double area = face.area();
            if (area > maxArea) {
                maxArea = area;
                mainFace = face;
            }
        }
        if (maxArea < 2500) mainFace = null;

        // Atualiza status e anti-flicker
        String currentStatus = "Nenhum Rosto";
        Color currentColor = Color.RED;

        if (mainFace != null) {
            noFaceCount = 0;

            int centerX = mainFace.x + mainFace.width / 2;
            int centerY = mainFace.y + mainFace.height / 2;

            // Determina posição do olhar
            double horizontalThreshold = 0.30;
            double verticalThreshold = 0.35;

            if (centerX < frame.width() * horizontalThreshold) {
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
                currentColor = new Color(0, 150, 0);
            }

            // Desenha retângulo e nome
            Imgproc.rectangle(frame, mainFace,
                    new Scalar(currentColor.getBlue(), currentColor.getGreen(), currentColor.getRed()), 2);
            Imgproc.putText(frame, studentName, new Point(mainFace.x, mainFace.y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7,
                    new Scalar(currentColor.getBlue(), currentColor.getGreen(), currentColor.getRed()), 2);

            gui.updateFaceStatus(currentStatus, currentColor);

            if (!currentStatus.equals(lastFaceStatus)) {
                String action = currentStatus.replaceAll(" ", "_").replaceAll("[()]", "").toUpperCase();

                // Envia evento ao servidor e registra logs
                eventSender.sendEventAsync("vision", action);
                String logMessage = String.format("[%s | %s] Rosto: %s (SENT)", studentName, examName, currentStatus);
                gui.addLog(logMessage);
                FileLogger.logTxt(logMessage);
                FileLogger.logJson("Rosto", currentStatus, 2);

                lastFaceStatus = currentStatus;
            }

        } else {
            // Nenhum rosto detectado
            noFaceCount++;
            if (noFaceCount >= MAX_NO_FACE_FRAMES && !"Nenhum Rosto".equals(lastFaceStatus)) {
                String action = "NO_FACE_LONG";
                eventSender.sendEventAsync("vision", action);

                String logMessage = String.format("[%s | %s] Rosto: Nenhum Rosto (SENT)", studentName, examName);
                gui.addLog(logMessage);
                FileLogger.logTxt(logMessage);
                FileLogger.logJson("Rosto", "Nenhum Rosto", 2);

                gui.updateFaceStatus("Nenhum Rosto", Color.RED);
                Imgproc.putText(frame, "ROSTO NAO DETECTADO", new Point(50, 50),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 1, new Scalar(0, 0, 255), 2);

                lastFaceStatus = "Nenhum Rosto";
            }
        }

        gray.release();
    }

    /** Detecta movimento e atualiza status da GUI/Servidor */
    private void detectMotion(Mat frame) {
        motionDetector.apply(frame, foregroundMask);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        // Limpeza de ruído
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(foregroundMask, foregroundMask, Imgproc.MORPH_OPEN, kernel);

        Imgproc.findContours(foregroundMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        boolean motionDetected = false;

        for (MatOfPoint contour : contours) {
            if (Imgproc.contourArea(contour) > 1000) {
                motionDetected = true;
                Rect rect = Imgproc.boundingRect(contour);
                Imgproc.rectangle(frame, rect, new Scalar(0, 0, 255), 1);
            }
        }

        if (motionDetected != lastMotionStatus) {
            String msg = motionDetected ? "Movimento detectado" : "Movimento cessado";
            String detail = motionDetected ? "MOTION_DETECTED" : "MOTION_CEASED";

            eventSender.sendEventAsync("vision", detail);
            gui.addLog("[" + studentName + " | " + examName + "] " + msg + " (SENT)");
            FileLogger.logTxt("[" + studentName + " | " + examName + "] " + msg);
            FileLogger.logJson("vision", detail, 2);

            lastMotionStatus = motionDetected;
        }

        gui.updateMotionStatus(motionDetected ? "Detectado" : "Estável",
                motionDetected ? Color.RED : new Color(0, 150, 0));

        hierarchy.release();
    }
}
