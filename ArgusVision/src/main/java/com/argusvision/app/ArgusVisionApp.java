package com.argusvision.app;

import org.opencv.core.Core;

import com.argusvision.camera.HeadlessVisionOutput;
import com.argusvision.camera.VisionMonitor;
import com.argusvision.camera.VisionOutput;
import com.argusvision.util.FileLogger;
import com.argusvision.util.VisionContext;
import com.argusvision.model.Session;
import com.argusvision.net.SessionClient;

public class ArgusVisionApp {

	public static void main(String[] args) {

		// 1️⃣ Carregar biblioteca OpenCV
		try {
			System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
			System.out.println("OpenCV carregado com sucesso!");
		} catch (UnsatisfiedLinkError e) {
			System.err.println("ERRO CRÍTICO: Não foi possível carregar OpenCV.");
			System.exit(1);
		}

		System.out.println("================================================");
		System.out.println("      ArgusVision - Monitoramento Visual");
		System.out.println("================================================");

		String student = args.length > 0 ? args[0] : "AlunoTeste";

		Session s;
		// 2️⃣ Buscar sessão ANTES de iniciar câmera
		
		try {
			s = SessionClient.fetch();
		    //s = SessionClient.fetchByStudent(student);
		} catch (RuntimeException e) {
		    System.out.println("Nenhuma sessão ativa para este aluno. Encerrando.");
		    return;
		}

		//Session s = SessionClient.start(student, exam);

		// 3️⃣ Inicializar contexto global
		VisionContext.init(s.getStudent(), s.getExam(), s.getSession());

		// 4️⃣ Criar GUI
		//CameraViewer gui = new CameraViewer();

		// 5️⃣ Mostrar GUI e identidade
//		SwingUtilities.invokeLater(() -> {
//			gui.setVisible(true);
//			gui.setIdentity(s.getStudent(), s.getExam());
//			gui.setStatus("Iniciando monitoramento...");
//		});

		VisionOutput output = new HeadlessVisionOutput();
		
		// 6️⃣ Criar Monitor
		//VisionMonitor monitor = new VisionMonitor(s.getStudent(), s.getExam(), output);
		VisionMonitor monitor = new VisionMonitor(s.getStudent(), s.getExam(), s.getSession(), output);
		System.out.println("\nIniciando câmera e algoritmos...");
		monitor.start();

		// 7️⃣ Encerramento controlado
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Encerrando sistema...");
			monitor.stop();
			//monitor.release();
			FileLogger.closeJsonLog();
		}));
	}
}
