package com.googlecode.javacv.facepreview.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.view.View;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_objdetect;
import com.googlecode.javacv.cpp.opencv_objdetect.CvHaarClassifierCascade;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;
import static com.googlecode.javacv.cpp.opencv_core.cvClearMemStorage;
import static com.googlecode.javacv.cpp.opencv_core.cvGetSeqElem;
import static com.googlecode.javacv.cpp.opencv_core.cvLoad;
import static com.googlecode.javacv.cpp.opencv_objdetect.CV_HAAR_FIND_BIGGEST_OBJECT;
import static com.googlecode.javacv.cpp.opencv_objdetect.cvHaarDetectObjects;

// can we use startFaceDetection on camera? probably not
public class FaceView extends View implements Camera.PreviewCallback {
    public static final int CONSISTENCY_SUBSAMPLING_FACTOR = 8;
    public static final int RECOGNITION_SUBSAMPLING_FACTOR = 4;

    public IplImage grayImage;
    public String displayedText = "点击屏幕以拍摄头像 - 此端在上.";

    private CvHaarClassifierCascade classifier;
    private CvMemStorage storage;
    private CvSeq faces;

    public FaceView(Context context) throws IOException {
        super(context);

        // Load the classifier file from Java resources.
        File classifierFile = Loader.extractResource(getClass(),
                "/com/googlecode/javacv/facepreview/data/haarcascade_frontalface_alt.xml",
                context.getCacheDir(), "classifier", ".xml");
        if (classifierFile == null || classifierFile.length() <= 0) {
            throw new IOException("Could not extract the classifier file from Java resource.");
        }

        // Preload the opencv_objdetect module to work around a known bug.
        Loader.load(opencv_objdetect.class);
        classifier = new CvHaarClassifierCascade(cvLoad(classifierFile.getAbsolutePath()));
        classifierFile.delete();
        if (classifier.isNull()) {
            throw new IOException("Could not load the classifier file.");
        }
        storage = CvMemStorage.create();

    }

    public void onPreviewFrame(final byte[] data, final Camera camera) {
        try {
            Camera.Size size = camera.getParameters().getPreviewSize();
            processImage(data, size.width, size.height);
            camera.addCallbackBuffer(data);
        } catch (RuntimeException e) {
            // The camera has probably just been released, ignore.
            System.err.println(e.toString());
        }
    }

    private void createSubsampledImage(byte[] data, int width, int height, int f, IplImage subsampledImage) {
        // TODO: speed this up
        int imageWidth  = subsampledImage.width();
        int imageHeight = subsampledImage.height();
        int dataStride = f*width;
        int imageStride = subsampledImage.widthStep();
        ByteBuffer imageBuffer = subsampledImage.getByteBuffer();
        for (int y = 0; y < imageHeight; y++) {
            int dataLine = y*dataStride;
            int imageLine = y*imageStride;
            for (int x = 0; x < imageWidth; x++) {
                imageBuffer.put(imageLine + x, data[dataLine + f*x]);
            }
        }
    }

    // on main thread
    protected void processImage(byte[] data, int width, int height) {
        if (grayImage == null || grayImage.width() != width/CONSISTENCY_SUBSAMPLING_FACTOR || grayImage.height() != height/CONSISTENCY_SUBSAMPLING_FACTOR) {
            try {
                grayImage = IplImage.create(width/CONSISTENCY_SUBSAMPLING_FACTOR, height/CONSISTENCY_SUBSAMPLING_FACTOR, IPL_DEPTH_8U, 1);
            } catch (Exception e) {
                // ignore exception. It is only a warning in this case
                System.err.println(e.toString());
            }
        }
        createSubsampledImage(data, width, height, CONSISTENCY_SUBSAMPLING_FACTOR, grayImage);

        // detect face
        cvClearMemStorage(storage);
        faces = cvHaarDetectObjects(grayImage, classifier, storage, 1.1, 3, CV_HAAR_FIND_BIGGEST_OBJECT);
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(30);

        float textWidth = paint.measureText(displayedText);
        canvas.drawText(displayedText, (getWidth()-textWidth)/2, 30, paint);

        paint.setStrokeWidth(2);
        paint.setColor(Color.RED);

        if (faces != null) {
            paint.setStrokeWidth(2);
            paint.setStyle(Paint.Style.STROKE);
            float scaleX = (float)getWidth()/grayImage.width();
            float scaleY = (float)getHeight()/grayImage.height();
            int total = faces.total();
            for (int i = 0; i < total; i++) {//should only be 1
                CvRect r = new CvRect(cvGetSeqElem(faces, i));
                int x = r.x(), y = r.y(), w = r.width(), h = r.height();
                //Commented out code works if using back facing camera
                //canvas.drawRect(x*scaleX, y*scaleY, (x+w)*scaleX, (y+h)*scaleY, paint);
                canvas.drawRect(getWidth()-x*scaleX, y*scaleY, getWidth()-(x+w)*scaleX, (y+h)*scaleY, paint);
            }
        }

    }
}
