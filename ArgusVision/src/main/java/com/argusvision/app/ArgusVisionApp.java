package main.java.com.argusvision.app;

import org.opencv.core.Core;

import main.java.com.argusvision.CameraViewer;
import main.java.com.argusvision.VisionMonitor;
import main.java.com.argusvision.util.FileLogger;

import javax.swing.SwingUtilities;
import java.util.Scanner;

public class ArgusVisionApp {
	
    public static void main(String[] args) {
        // 1. Carregar biblioteca OpenCV
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

        // 2. Preparar a Interface Gráfica (GUI)
        // Criamos a janela, mas não iniciamos a câmera DENTRO dela.
        // Ela vai apenas receber as imagens do Monitor.
        CameraViewer gui = new CameraViewer();

        // 3. Coletar dados (Console)
        Scanner scanner = new Scanner(System.in);

        System.out.print("Nome do aluno: ");
        String inputStudent = scanner.nextLine().trim();
        String studentName = inputStudent.isEmpty() ? "Aluno Teste" : inputStudent;
        
        System.out.print("Nome do exame: ");
        String inputExam = scanner.nextLine().trim();
        String examName = inputExam.isEmpty() ? "Exame Simulado" : inputExam;
        
        // 4. Mostrar a GUI agora
        SwingUtilities.invokeLater(() -> {
            gui.setVisible(true);
            gui.setIdentity(studentName, examName);
            gui.setStatus("Iniciando monitoramento...");
        });

        // 5. Iniciar o Monitor passando a GUI como referência
        // O Monitor vai capturar a imagem -> processar -> atualizar a GUI
        VisionMonitor monitor = new VisionMonitor(studentName, examName, gui);

        System.out.println("\nIniciando câmera e algoritmos...");
        monitor.start();

        System.out.println("Pressione ENTER no console para encerrar (ou feche a janela).");
        scanner.nextLine(); // Aguarda Enter

        System.out.println("Parando sistema...");
        monitor.stop();
        monitor.release();

        // !!! NOVO: Fechar o log JSON
        FileLogger.closeJsonLog();

        gui.dispose(); // Fecha a janela
        System.exit(0);
    }
}