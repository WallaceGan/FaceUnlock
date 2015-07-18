package com.googlecode.javacv.facepreview.compute;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.googlecode.javacv.cpp.opencv_core.CvRect;

public class BackgroundConsistencyAnalysis {

	// last 100 frames: number of pixels in face that were considered in the foreground
	private LinkedList<Integer> motionTrendFace = new LinkedList<Integer>(); 
	// last 100 frames: number of pixels outside the face that were considered in the foreground
	private LinkedList<Integer> motionTrendNotFace = new LinkedList<Integer>();
	
	private LinkedList<Double> motion_metric_ratio_chart = new LinkedList<Double>();
	
	// need to write this ourselves, since there is a bug in Bitmap.getPixel()
	private byte getPixel(ByteBuffer forgroundMap, int height, int x, int y) {
		return forgroundMap.get(y*height + x);
	}
	private int length = 40;
	
	public void processNewFrame(ByteBuffer forgroundMap, int mapHeight, int mapWidth, CvRect faceRectangle) {
		
		if (faceRectangle.isNull()) {
			// TODO: consider this case as well
			return;
		}
	
		int faceRectangleCount = 0;
		for (int j = faceRectangle.y(); j < faceRectangle.y() + faceRectangle.height(); j++) {
			for (int i = faceRectangle.x(); i < faceRectangle.x() + faceRectangle.width(); i++) {
				if (getPixel(forgroundMap, mapHeight, i, j) != 0) {
					faceRectangleCount++;
				}
			}
		}
		
		int nonFaceRectangleCount = -faceRectangleCount;
		for (int j = 0; j < mapHeight; j++) {
			for (int i = 0; i < mapWidth; i++) {
				if (getPixel(forgroundMap, mapHeight, i, j) != 0) {
					nonFaceRectangleCount++;
				}
			}
		}
		
		if (nonFaceRectangleCount + faceRectangleCount == 0) {
			// return, since we don't want to divide by zero
			return;
		}
		
		motionTrendFace.add(faceRectangleCount);
		motionTrendNotFace.add(nonFaceRectangleCount);
		if (motionTrendFace.size() >= length) {
			motionTrendFace.removeFirst();
			motionTrendNotFace.removeFirst();
		}
		
		// Calculate CMD by summing the last n of these values
		double motion_metric_i = (nonFaceRectangleCount - faceRectangleCount)*(nonFaceRectangleCount - faceRectangleCount)/(nonFaceRectangleCount + faceRectangleCount);
		motion_metric_ratio_chart.add(motion_metric_i);
		if (motion_metric_ratio_chart.size() >= length) {
			motion_metric_ratio_chart.removeFirst();
		}
		
	}
	
	// Draws a chart of how CDM changes over time
	public void drawChartCMD(Canvas c, Paint p) {
		if (!pass()) {
			p.setColor(Color.RED);
		} else {
			p.setColor(Color.GREEN);
		}
		Iterator<Double> it1 = motion_metric_ratio_chart.iterator();
		for (int i = 0; i < length; i++) {
			if (!it1.hasNext())
				break;
			double value = it1.next()/500.0;//divide by arbitrarily large value
			value = Math.min(500, value);
			Rect r = new Rect(i*3, 600-(int)(value*100), i*3+3, 600);
			c.drawRect(r, p);
		}
		c.drawText("Face motion: " + totalMotionTrendFace() + " / " + faceMotionMin, 0, 630, p);
		c.drawText("CMD ratio: " + CMD() + " / " + maxCMD, 0, 660, p);
	}
	
	// arbitrarily chosen for now.
	private final static int faceMotionMin = 500;
	private final static double maxCMD = 4000;
	
	private int totalMotionTrendFace() {
		int totalMotionTrendFace  = 0;
		for (Integer value : motionTrendFace) {
			totalMotionTrendFace += value;
		}
		return totalMotionTrendFace;
	}
	
	private double CMD() {
		double CMD = 0;
		for (Double value : motion_metric_ratio_chart) {
			CMD += value;
		}
		return CMD;
	}
	
	// A image of a face is likely a spoof, if either of the following is true:
	// 	-The face has no motion
	//  -The forground motion outside of the face is highly correlated with the face's forground motion (ie, a picture frame moves with the picture).
	public boolean pass() {
		if (motionTrendFace.size() < length - 1) 
			return false;
		
		// sum motion_metric_ratio_chart and motionTrendFace
		return (totalMotionTrendFace() >= faceMotionMin) && (CMD() <= maxCMD) ;
	}
	
}
