package com.argusvision.util;

public class SessionDTO {

    private String student;
    private String exam;
    private String session;

    public SessionDTO() {}

    public SessionDTO(String student, String exam, String session) {
        this.student = student;
        this.exam = exam;
        this.session = session;
    }

    public String getStudent() {return student;}
    public String getExam() {return exam;}
    public String getSession() {return session;}
}
