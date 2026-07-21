package com.argusvision.app;

import org.opencv.core.Core;

import com.argusvision.camera.HeadlessVisionOutput;
import com.argusvision.camera.VisionMonitor;
import com.argusvision.camera.VisionOutput;
import com.argusvision.util.FileLogger;
import com.argusvision.util.LocalSessionReader;
import com.argusvision.util.SingleInstanceGuard;
import com.argusvision.util.VisionContext;
import com.argusvision.model.Session;
import com.argusvision.net.SessionClient;

public class ArgusVisionApp {

	public static void main(String[] args) {

		// 0️⃣ Só pode existir UM ArgusVision por máquina - só existe uma webcam física.
		// Sem essa trava, dois logins na mesma máquina disputam a mesma câmera e
		// o segundo processo pode "roubar" o feed do primeiro.
		if (!SingleInstanceGuard.acquire()) {
			System.err.println("ERRO: já existe uma instância do ArgusVision rodando nesta máquina.");
			System.err.println("Só uma webcam por máquina - encerrando esta segunda instância.");
			System.exit(1);
			return;
		}

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

		// Ordem de prioridade para descobrir o aluno, sem nunca perguntar de novo:
		// 1) argumento de linha de comando (o próprio plugin já lança assim);
		// 2) sessão gravada localmente pelo plugin no login (SessionHandoff).
		String student = (args.length > 0 && !args[0].isBlank())
				? args[0]
				: LocalSessionReader.readStudent();

		if (student == null || student.isBlank()) {
			System.err.println("ERRO: nome do aluno não informado (nem por argumento, nem pela sessão local do Argus).");
			System.err.println("Verifique se o plugin Argus já está com uma sessão ativa nesta máquina.");
			System.exit(1);
			return;
		}
		System.out.println("Aluno identificado: " + student);

		Session s;

		try {
			s = SessionClient.fetchByStudent(student);
		} catch (RuntimeException e) {
		    System.out.println("Nenhuma sessão ativa para o aluno " + student + ". Encerrando.");
		    return;
		}

		VisionContext.init(s.getStudent(), s.getExam(), s.getSession());

		VisionOutput output = new HeadlessVisionOutput();

		VisionMonitor monitor = new VisionMonitor(s.getStudent(), s.getExam(), s.getSession(), output);
		System.out.println("\nIniciando câmera e algoritmos...");
		monitor.start();

		// 🔴 NOVO: thread de monitoramento da sessão
		new Thread(() -> {
		    while (true) {
		        try {
		            Thread.sleep(3000); // checa a cada 3s

		            Session active = SessionClient.fetchByStudent(student);

		            // se a sessão mudou ou não existe → encerra
		            if (!active.getSession().equals(s.getSession())) {
		                System.out.println("Sessão encerrada pelo servidor.");
		                monitor.stop();
		                break;
		            }

		        } catch (Exception e) {
		            // fetch falhou → provavelmente não existe mais sessão
		            System.out.println("Sessão não encontrada. Encerrando Vision...");
		            monitor.stop();
		            break;
		        }
		    }
		}).start();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Encerrando sistema...");
			monitor.stop();
			//monitor.release();
		}));
	}
}
