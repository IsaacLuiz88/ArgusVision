package com.argusvision.camera;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.util.Base64;

public class FrameEncoder {
	public static String encodeToBase64(Mat frame) {
		MatOfByte buffer = new MatOfByte();

		// JPEG com compressão (IMPORTANTE)
        Imgcodecs.imencode(".jpg", frame, buffer);
        
        byte[] bytes = buffer.toArray();
        return Base64.getEncoder().encodeToString(bytes);
	}
}
