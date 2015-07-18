package com.googlecode.javacv.facepreview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.MediaStore;
import android.util.Pair;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacv.cpp.opencv_contrib.FaceRecognizer;
import com.googlecode.javacv.cpp.opencv_core.CvMat;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.CvSize;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_core.MatVector;
import com.googlecode.javacv.cpp.opencv_objdetect;
import com.googlecode.javacv.cpp.opencv_objdetect.CvHaarClassifierCascade;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static com.googlecode.javacv.cpp.opencv_core.CV_32SC1;
import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;
import static com.googlecode.javacv.cpp.opencv_core.cvClearMemStorage;
import static com.googlecode.javacv.cpp.opencv_core.cvCreateImage;
import static com.googlecode.javacv.cpp.opencv_core.cvCreateMat;
import static com.googlecode.javacv.cpp.opencv_core.cvGet2D;
import static com.googlecode.javacv.cpp.opencv_core.cvGetSeqElem;
import static com.googlecode.javacv.cpp.opencv_core.cvGetSize;
import static com.googlecode.javacv.cpp.opencv_core.cvLoad;
import static com.googlecode.javacv.cpp.opencv_core.cvSetImageROI;
import static com.googlecode.javacv.cpp.opencv_highgui.cvLoadImage;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_BGR2GRAY;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_INTER_LINEAR;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvCvtColor;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvEqualizeHist;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvResize;
import static com.googlecode.javacv.cpp.opencv_objdetect.CV_HAAR_FIND_BIGGEST_OBJECT;
import static com.googlecode.javacv.cpp.opencv_objdetect.cvHaarDetectObjects;

// Purpose: trains and uses a FaceRecognizer class to perform authorization
// Currently, this learns several people's faces. The authorize()
// function returns true if their face is more similar to the authorized
// face than anyone else's face. We could simplify this: only compare the user's
// face with the authorized person's face and return true iff difference <= threshold.
public class FacePredictor {
      
    // see description of different algorithms here: http://docs.opencv.org/trunk/modules/contrib/doc/facerec/facerec_api.html
    private static final Double THRESHHOLD = Double.MAX_VALUE/2; // higher means a loser threshold.
    private static final FaceRecognizer ALGO_FACTORY =
        com.googlecode.javacv.cpp.opencv_contrib.createLBPHFaceRecognizer(1, 8, 8, 8, THRESHHOLD);
    private static final Map<Integer, String> names = new HashMap<Integer, String>();
    public final FaceRecognizer algorithm;

    private Context context; // store for debugging    

    // Load from file
    public FacePredictor(Context applicationContext, String filename) throws Exception {
  	    File file = new File(applicationContext.getFilesDir() + "/" + filename);
  	    if (!file.exists()) {
  	    	throw new Exception();
  	    }
  	    
  	    this.context = applicationContext;
  	    loadClassifier();
  	  	algorithm = ALGO_FACTORY;
  	    algorithm.load(context.getExternalFilesDir(null).getAbsolutePath() + "/" + filename);
    }
    
    // This is a slow function. It is slow, because it has to load a lot of images.
    // This doesn't need to be a problem. 
    public FacePredictor(Context context, IplImage [] authorizedImages) throws IOException {
      
      this.context = context;
      loadClassifier();
      
      final int numberOfImages = (8+1)*3; // TODO: calculate this more smartly.. maybe don't need to calculate
      final MatVector images = new MatVector(numberOfImages);
      final CvMat labels = cvCreateMat(1, numberOfImages, CV_32SC1);
     
      int imgCount = 0;      
      addNameAndFace(authorizedImages[0], imgCount++, 11, images, labels);
      addNameAndFace(authorizedImages[1], imgCount++, 11, images, labels);
      addNameAndFace(authorizedImages[2], imgCount++, 11, images, labels);
      
      // TODO: process these images ahead of time (otherwise startup will take several minutes)
      for (int personCount = 2; personCount < 10; personCount++) { // training people 2-10
      	// TODO: use a couple images per person. We have the four images per person available. I'm just not using them.
          String fileName = String.format("/com/googlecode/javacv/facepreview/data/a_%02d_05.jpg", personCount);
          addNameAndFace(fileName, imgCount, personCount, images, labels);
          imgCount++;
          
          fileName = String.format("/com/googlecode/javacv/facepreview/data/a_%02d_15.jpg", personCount);
          addNameAndFace(fileName, imgCount, personCount, images, labels);
          imgCount++;
          
          fileName = String.format("/com/googlecode/javacv/facepreview/data/b_%02d_15.jpg", personCount);
          addNameAndFace(fileName, imgCount, personCount, images, labels);
          imgCount++;
          
      }
      
      assert (numberOfImages == labels.size());
      
      this.algorithm = ALGO_FACTORY;
      algorithm.train(images, labels);
   }
    
  private void addNameAndFace(String fileName, int imgCount, int personCount, MatVector images, CvMat labels) throws IOException {
      File imageFile = Loader.extractResource(getClass(), fileName,
              context.getCacheDir(), "image", ".jpg");
      IplImage image = cvLoadImage(imageFile.getAbsolutePath()); // according to traceView, this is 75% of the time
      addNameAndFace(image, imgCount, personCount, images, labels);
  }
  
  private void addNameAndFace(IplImage image, int imgCount, int personCount, MatVector images, CvMat labels) throws IOException {
      IplImage grayImage = IplImage.create(image.width(), image.height(), IPL_DEPTH_8U, 1);
      cvCvtColor(image, grayImage, CV_BGR2GRAY);
 
      CvRect faceRectangle = detectFace(grayImage);  
      images.put(imgCount, toTinyGray(image, faceRectangle));
      labels.put(imgCount, personCount);
      String name = new Integer(personCount).toString();
  }
  
  private void loadClassifier() throws IOException {
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
  }
  
  public boolean authenticate(IplImage image) {
	  String name = identify(image).first;
	  return name != null && name.equals("11");
  }
  
 /*
  
  // NOT EVER USED
  Pair<String, Double> identify(IplImage image, CvRect face) {
    final IplImage iplImage = toTinyGray(image, face);
    final int[] prediction = new int[1];
    final double[] confidence = new double[1];
    algorithm.predict(iplImage, prediction, confidence);
    String name = names.get(prediction[0]);
    Double confidence_ = 100*(THRESHHOLD - confidence[0])/THRESHHOLD;
    return new Pair<String, Double>(name, confidence_); 
  }
  
  */
  
  // Input needs to be B
  public Pair<String, Double> identify(IplImage image) {
    // Convert to grayscale, if not already done
	IplImage grayImage;
    if (image.nChannels() == 1) {
    	grayImage = image;
    } else {
    	grayImage = IplImage.create(image.width(), image.height(), IPL_DEPTH_8U, 1);
    	cvCvtColor(image, grayImage, CV_BGR2GRAY);
    }
    
    CvRect faceRectangle = detectFace(grayImage);
    if (faceRectangle.isNull()) {
    	return new Pair<String, Double>(null, (double) 0); 
    }
	  
    final IplImage iplImage = toTiny(grayImage, faceRectangle);
    final int[] prediction = new int[1];
    final double[] confidence = new double[1];
    algorithm.predict(iplImage, prediction, confidence);
    //String name = names.get(prediction[0]);
    String name = new Integer(prediction[0]).toString();
    Double confidence_ = 100*(THRESHHOLD - confidence[0])/THRESHHOLD;

    // we return the identity with the highest confidence rating 
    for (int i = 0; i < confidence.length; i++) {
    	assert(confidence_ >= confidence[i]);
    }
    return new Pair<String, Double>(name, confidence_); 
  }

  private static final CvMemStorage storage = CvMemStorage.create();
  private static CvHaarClassifierCascade classifier ;

  static int debugPictureCount = 0;
  /**
   * This does facial detection and NOT facial recognition
   */
  private synchronized CvRect detectFace(IplImage image) {
	cvClearMemStorage(storage);

    final CvSeq cvSeq = cvHaarDetectObjects(image, classifier, storage, 1.1, 3, CV_HAAR_FIND_BIGGEST_OBJECT);
    assert !cvSeq.isNull();

    return new CvRect(cvGetSeqElem(cvSeq, 0));
  }

  private static final CvSize SMALL_IMAGE_SIZE = new CvSize(400,400);
  
  /**
   * Images should be grayscaled and scaled-down for faster calculations
   */
  private IplImage toTinyGray(IplImage image, CvRect r /* (x,y) is the top corner */) {
      IplImage gray = cvCreateImage(cvGetSize(image), IPL_DEPTH_8U, 1);
      cvCvtColor(image, gray, CV_BGR2GRAY);		
      return toTiny(gray, r);
  }
  
  private IplImage toTiny(IplImage gray, CvRect r /* (x,y) is the top corner */) {
      IplImage roi = cvCreateImage(SMALL_IMAGE_SIZE, IPL_DEPTH_8U, 1);

      int width = Math.max(r.width(), r.height());
      int x = r.x() + (r.width()-width)/2;
      int y = r.y() + (r.height()-width)/2;
      
      CvRect r1 = new CvRect(x, y, width, width);// consider adding +10 on all sides
      cvSetImageROI(gray, r1);//set portion that will be processed on
      cvResize(gray, roi, CV_INTER_LINEAR);
      cvEqualizeHist(roi, roi);
      	//debugPrintIplImage(roi, context);
      return roi;  
  }

	public void save(Context applicationContext, String filename) {		
		algorithm.save(context.getExternalFilesDir(null).getAbsolutePath() + "/" + filename);
	}
	

    // Save IplImage to disk, so we can look at it later
    public static void debugPrintIplImage(IplImage src, Context context) {
    	Bitmap tmpbitmap = IplImageToBitmap(src);
        MediaStore.Images.Media.insertImage(context.getContentResolver(), tmpbitmap, "image" + Calendar.getInstance().get(Calendar.SECOND) + debugPictureCount++ , "temp");
    }
    
    private static Bitmap IplImageToBitmap(IplImage src) {// can't use cvSave, since that would save into a mocked context
        int width = src.width();
        int height = src.height();
        int smallFactor = 4;
        Bitmap bitmap = Bitmap.createBitmap(width/smallFactor, height/smallFactor, Bitmap.Config.ARGB_8888);
        for(int r=0;r<height/smallFactor;r+=1) {
            for(int c=0;c<width/smallFactor;c+=1) {
                int gray = (int) Math.floor(cvGet2D(src,r*smallFactor,c*smallFactor).getVal(0));
                bitmap.setPixel(c, r, Color.argb(255, gray, gray, gray));
            }
        }
        return bitmap;
    }
}