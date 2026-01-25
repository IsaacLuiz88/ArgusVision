package com.argusvision.camera;

import org.opencv.core.Mat;
import java.awt.Color;
import com.argusvision.util.FileLogger;

public class HeadlessVisionOutput implements VisionOutput {

	@Override
	public void updateFrame(Mat frame) {
        // Ignora — frame já é enviado via VisionEventSender
    }

	@Override
    public void updateFaceStatus(String status, Color color) {
        FileLogger.logTxt("[FACE] " + status);
    }

    @Override
    public void setStatus(String status) {
        FileLogger.logTxt("[STATUS] " + status);
    }

    @Override
    public void addLog(String message) {
        FileLogger.logTxt("[LOG] " + message);
    }
}
