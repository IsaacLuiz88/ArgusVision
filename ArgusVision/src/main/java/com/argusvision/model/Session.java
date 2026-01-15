package com.argusvision.model;

public class Session {

    private String student;
    private String exam;
    private String session;

    public Session() {}

    public Session(String student, String exam, String session) {
        this.student = student;
        this.exam = exam;
        this.session = session;
    }

    public String getStudent() {return student;}
    public String getExam() {return exam;}
    public String getSession() {return session;}

    public void setStudent(String student) {this.student = student;}
    public void setExam(String exam) {this.exam = exam;}
    public void setSession(String session) {this.session = session;}
}
