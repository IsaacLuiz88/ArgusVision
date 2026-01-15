package com.argusvision.util;

public class VisionContext {

    public static String student = "EstudanteDesconhecido";
    public static String exam = "ExameDesconhecido";
    public static String session = "SessaoDesconhecida";

    public static void init(String studentName, String examName, String sessionId) {
        VisionContext.student = studentName;
        VisionContext.exam = examName;
        VisionContext.session = sessionId;
    }
}
