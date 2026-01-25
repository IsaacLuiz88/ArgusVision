package com.argusvision.camera;

import org.opencv.core.Mat;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CameraViewer extends JFrame implements VisionOutput {
    
    private JLabel cameraLabel;
    private JLabel statusLabel;
    private JLabel faceLabel;
    private JLabel motionLabel;
    private JTextArea logArea;
    private JLabel identityLabel;

    public CameraViewer() {
        initComponents();
        setTitle("ArgusVision - Monitoramento em Tempo Real");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Fecha tudo se fechar a janela
        setLocationRelativeTo(null);
    }
    
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Painel de Vídeo
        JPanel cameraPanel = new JPanel(new BorderLayout());
        cameraPanel.setBorder(BorderFactory.createTitledBorder("Feed da Câmera"));
        cameraPanel.setBackground(Color.BLACK);
        
        cameraLabel = new JLabel("Aguardando câmera...", SwingConstants.CENTER);
        cameraLabel.setForeground(Color.WHITE);
        cameraPanel.add(cameraLabel, BorderLayout.CENTER);
        
        // Painel de Status (Topo)
        JPanel topPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        statusLabel = createStatusLabel("Sistema: Aguardando", Color.GRAY);
        faceLabel = createStatusLabel("Rosto: --", Color.GRAY);
        motionLabel = createStatusLabel("Movimento: --", Color.GRAY);
        
        topPanel.add(statusLabel);
        topPanel.add(faceLabel);
        topPanel.add(motionLabel);
        
        // Painel de Logs (Baixo)
        logArea = new JTextArea(6, 50);
        logArea.setEditable(false);
        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log de Eventos"));

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(cameraPanel, BorderLayout.CENTER);
        mainPanel.add(scrollLog, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        this.identityLabel = new JLabel("Aluno: -- | Prova: --", SwingConstants.CENTER);
        this.identityLabel.setFont(new Font("Arial", Font.BOLD, 16));
        this.identityLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(identityLabel, BorderLayout.WEST);
    }

    private JLabel createStatusLabel(String text, Color color) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Arial", Font.BOLD, 14));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(230, 230, 230));
        lbl.setForeground(color);
        lbl.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        return lbl;
    }

    // --- MÉTODOS PÚBLICOS CHAMADOS PELO VISIONMONITOR ---

    /**
     * Recebe um frame (Mat) do OpenCV, converte e exibe na tela.
     */
    public void updateFrame(Mat mat) {
        if (mat == null || mat.empty()) return;

        // Converter Mat para BufferedImage
        BufferedImage image = matToBufferedImage(mat);

        // Atualizar na Thread da GUI
        SwingUtilities.invokeLater(() -> {
            if (image != null) {
                // Redimensionar para caber no label se necessário, ou exibir direto
                cameraLabel.setText("");
                cameraLabel.setIcon(new ImageIcon(image));
                cameraLabel.repaint();
            }
        });
    }

    public void updateFaceStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            faceLabel.setText(text);
            faceLabel.setForeground(color);
        });
    }

    public void updateMotionStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            motionLabel.setText(text);
            motionLabel.setForeground(color);
        });
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    public void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + message + "\n");

            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // Método utilitário de conversão
    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.get(0, 0, buffer);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
        return image;
    }

    public void setIdentity(String student, String exam) {
        SwingUtilities.invokeLater(() -> {
            identityLabel.setText("Aluno: " + student + " | Prova: " + exam);
        });
    }
}