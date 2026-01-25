package com.argusvision.camera;

import java.awt.Color;

import org.opencv.core.Mat;

public interface VisionOutput {
	void updateFrame(Mat frame);
    void updateFaceStatus(String status, Color color);
    void setStatus(String status);
    void addLog(String message);
}
