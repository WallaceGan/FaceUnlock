package com.googlecode.javacv.facepreview.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.view.View;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_objdetect;
import com.googlecode.javacv.cpp.opencv_objdetect.CvHaarClassifierCascade;
import com.googlecode.javacv.cpp.opencv_video.BackgroundSubtractorMOG2;
import com.googlecode.javacv.facepreview.FacePredictor;
import com.googlecode.javacv.facepreview.FacePredictorFactory;
import com.googlecode.javacv.facepreview.compute.BackgroundConsistencyAnalysis;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;
import static com.googlecode.javacv.cpp.opencv_core.cvClearMemStorage;
import static com.googlecode.javacv.cpp.opencv_core.cvGetSeqElem;
import static com.googlecode.javacv.cpp.opencv_core.cvLoad;
import static com.googlecode.javacv.cpp.opencv_highgui.cvSaveImage;
import static com.googlecode.javacv.cpp.opencv_objdetect.CV_HAAR_FIND_BIGGEST_OBJECT;
import static com.googlecode.javacv.cpp.opencv_objdetect.cvHaarDetectObjects;

// can we use startFaceDetection on camera? probably not
public class FaceViewWithAnalysis extends View implements PreviewCallback {
    public static final int CONSISTENCY_SUBSAMPLING_FACTOR = 8;
    public static final int RECOGNITION_SUBSAMPLING_FACTOR = 4;

    public IplImage grayImage;
    public IplImage largerGrayImage;
    private IplImage foreground;
    private Bitmap forgroundBitmap;
    public String displayedText = "使面部位于屏幕中央解锁 - 此端在上.";

    // used for quickly identifying face location
    private CvHaarClassifierCascade classifier;
    private CvMemStorage storage;
    private CvSeq faces;

    // used for recognizing whos face it is
    private FacePredictor facePredictor;
    private long lastUnixTime = System.currentTimeMillis();//note: currentTimeMillis shouldn't be used for subsecond

    // used for determining whether we are being shown a spoofed face (a pre-existing picture of the face)
    private BackgroundConsistencyAnalysis consistencyAnalysis = new BackgroundConsistencyAnalysis();
    private BackgroundSubtractorMOG2 backgroundSubtractor;

    private boolean faceRecognitionSuccess = false;
    private String recognizedFace = "";

    public FaceViewWithAnalysis(Context context) throws IOException {
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

        backgroundSubtractor = new BackgroundSubtractorMOG2();

        loadFacePredictor();
    }

    private void loadFacePredictor() {
    	new AsyncTask<Void, Void, FacePredictor>() {
			@Override
			protected FacePredictor doInBackground(Void... params) {
				return FacePredictorFactory.createFacePredictor(getContext());
			}
			@Override
			protected void onPostExecute(FacePredictor result) {
				facePredictor = result;
			}
    	}.execute();
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

    public interface SuccessCallback {
		void success(boolean b);
    }
    public void setSuccessCallback(SuccessCallback callback) {
    	mCallback = callback;
    }
    SuccessCallback mCallback = null;

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
    // the following could be pipelined (via ThreadPoolExecutor)
    // TODO: this more efficienty using built in API, or parallel for http://stackoverflow.com/questions/4010185/parallel-for-for-java
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

        // TODO: see if this callback is on the UI thread... if not, then the
        // below asynchronous thing probably shouldn't be asynchronous
        // or maybe not.. Perhaps we want 

        if (foreground == null) {
			foreground = IplImage.create(grayImage.width(),
					grayImage.height(), IPL_DEPTH_8U, 1);
		}

        // this function has linear variance
        final double learningRate = 0.05;
        backgroundSubtractor.apply(grayImage, foreground, learningRate);

   		// detect face
		cvClearMemStorage(storage);
		faces = cvHaarDetectObjects(grayImage, classifier, storage, 1.1, 3, CV_HAAR_FIND_BIGGEST_OBJECT);

        if (forgroundBitmap == null) {
    	    // This bitmap is only used for displaying on the canvas
   			forgroundBitmap = Bitmap.createBitmap(grayImage.width(), grayImage.height(), Config.ALPHA_8);
        }
   		forgroundBitmap.copyPixelsFromBuffer(foreground.getByteBuffer());
   		consistencyAnalysis.processNewFrame(foreground.getByteBuffer(), forgroundBitmap.getHeight(), forgroundBitmap.getWidth(), new CvRect(cvGetSeqElem(faces, 0)));
   		postInvalidate();

   		boolean everythingSuccessfulSoFar = consistencyAnalysis.pass();
   		performRecognitionTest(data, width, height, everythingSuccessfulSoFar);
    }

    private void performRecognitionTest(byte[] data, int width, int height, boolean everythingElseSuccessful) {
    	if (mCallback == null) {
    		return;
    	}

    	if (facePredictor == null) {
    		return;
    	}

		// Rate limit the analysis (should probably be done in the other function)
		if (System.currentTimeMillis() <= lastUnixTime + 4000) {
			return;
		}
		lastUnixTime = System.currentTimeMillis();

		initLargerGrayImageIfNecessary(width, height);
    	createSubsampledImage(data, width, height, RECOGNITION_SUBSAMPLING_FACTOR, largerGrayImage);
        if (debugPictureCount == 0) {
        	//debugPrintIplImage(grayImage, this.getContext());
        }

        if (!everythingElseSuccessful) {
        	mCallback.success(false);
        	// We don't need to perform the authentication step if consistency analysis failed
        	// However, we do it anyway for debugging purposes. Therefore the following line
        	// is commented out.
        	//return;
        }

    	// for the sake of safety, we are going to clone this image.
    	// On fast cpus, this isn't necessary. However, we want to be certain
    	// not to have two asynctasks messing with the same image.
    	final IplImage ownedImage = largerGrayImage.clone();

		new AsyncTask<Void, Void, Boolean>() {
			String name;

			@Override
			protected Boolean doInBackground(Void... n) {
				name = facePredictor.identify(ownedImage).first;
				System.out.println("name = " + name);
				return name != null && name.equals("11");
				//return facePredictor.authenticate(ownedImage);
			}
			@Override
			protected void onPostExecute(Boolean result) {
				mCallback.success(result);
				FaceViewWithAnalysis.this.recognizedFace = name;
				FaceViewWithAnalysis.this.faceRecognitionSuccess = result;
			}
		}.execute();
    }

	private void initLargerGrayImageIfNecessary(int width, int height) {
    	if (largerGrayImage == null || largerGrayImage.width() != width/RECOGNITION_SUBSAMPLING_FACTOR || largerGrayImage.height() != height/RECOGNITION_SUBSAMPLING_FACTOR) {
        	try {
        		largerGrayImage = IplImage.create(width/RECOGNITION_SUBSAMPLING_FACTOR, height/RECOGNITION_SUBSAMPLING_FACTOR, IPL_DEPTH_8U, 1);
        	} catch (Exception e) {
        		// ignore exception. It is only a warning in this case
        		System.err.println(e.toString());
        	}
        }
	}

    // todo: delete
    static int debugPictureCount = 0;
    private static void debugPrintIplImage(IplImage src, Context context) {
    	File file = new File(context.getExternalFilesDir(null), "testimage_same.jpg");
    	cvSaveImage(file.getAbsolutePath(), src);
    	debugPictureCount++;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = new Paint();

        paint.setTextSize(30);
        paint.setColor(Color.RED);
        if (this.faceRecognitionSuccess) {
        	paint.setColor(Color.GREEN);
        }
    	canvas.drawText("Face Recnogized", 0, 40, paint);
    	paint.setColor(Color.RED);
        if (this.consistencyAnalysis.pass()) {
        	paint.setColor(Color.GREEN);
        }
    	canvas.drawText("Consistency Analysis", 0, 80, paint);
    	paint.setColor(Color.BLUE);
    	canvas.drawText("Recognized face = " + this.recognizedFace, 0, 120, paint);

        paint.setTextSize(30);
        float textWidth = paint.measureText(displayedText);
        canvas.drawText(displayedText, (getWidth()-textWidth)/2, 30, paint);

        // show motion tracking, makes for a cool demo
        if (forgroundBitmap != null) {
        	paint.setColor(Color.BLACK);
            paint.setStrokeWidth(0);
            int startX = canvas.getWidth()-forgroundBitmap.getWidth();
            canvas.drawRect(startX, 0, startX+forgroundBitmap.getWidth(), forgroundBitmap.getHeight(), paint);
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(forgroundBitmap, startX, 0, paint);
        	//canvas.drawBitmap(forgroundBitmap, new Matrix(), paint);
        }

        consistencyAnalysis.drawChartCMD(canvas, paint);

        paint.setStrokeWidth(2);
        paint.setColor(Color.BLUE);

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
