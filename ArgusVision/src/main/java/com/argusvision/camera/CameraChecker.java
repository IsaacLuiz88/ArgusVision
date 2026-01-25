package com.argusvision.camera;

import org.opencv.videoio.VideoCapture;

public class CameraChecker {

    public static boolean hasAvailableCamera() {
        VideoCapture cap = new VideoCapture(0);

        if (!cap.isOpened()) {
            return false;
        }

        cap.release();
        return true;
    }
}
