package com.glasses_3d;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import min3d.Shared;
import min3d.core.Object3dContainer;
import min3d.core.RendererActivity;
import min3d.parser.IParser;
import min3d.parser.Parser;
import min3d.vos.Light;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.objdetect.CascadeClassifier;


public class Min3dActivity extends RendererActivity implements CvCameraViewListener2
{   
	private Object3dContainer mObject3DGlasses;
	private min3d.vos.Number3d mGlassesPosition = new min3d.vos.Number3d( 100.0f, 100.0f, 0.0f ); // Move the object out of visibility
	Point mPositionBetweenEyesOnPreview = new Point();
	float mGlassesScaleFactor = 1;
	
	private static final Scalar GOOD_NEWS_COLOUR = new Scalar(0, 255, 0, 255);
	private static final Scalar BAD_NEWS_COLOUR = new Scalar(255, 255, 0, 255);
	private Mat mRgba;
    private Mat mGray;
    private float mRelativeFaceSize = 0.2f;
    private int   mAbsoluteFaceSize = 0;
    private CascadeClassifier mFaceDetector;
    private File mCascadeFaceFile;
    private CascadeClassifier mEyesDetector;
    private File mCascadeEyesFile;
    private CameraBridgeViewBase mOpenCvCameraView;
    private int mPreviewShiftFromLeft;
    private int mPreviewShiftFromTop;
    private int mPreviewWidth;
    private int mPreviewHeight;
	
    //------- Min3D part --------------------------------------------
	@Override
	public void initScene() {
		
		// it is necessary to see the camera preview on the background
		scene.backgroundColor().setAll(0x00000000);
		
		// adding light sources to the scene
		scene.lights().add(new Light());
		scene.lights().add(new Light());
		Light myLight = new Light();    
		myLight.position.setZ(150); 
		scene.lights().add(myLight);
		
		// adding 3d glasses
		IParser parser = Parser.createParser(Parser.Type.OBJ,
				getResources(), "com.glasses_3d:raw/rayban_obj", true);
		parser.parse();

		mObject3DGlasses = parser.getParsedObject();
		scene.addChild(mObject3DGlasses);
	}

	@Override
	public void updateScene() {
		mObject3DGlasses.rotation().x = -7;
		mObject3DGlasses.position().x = mGlassesPosition.x;
		float MESH_SHIFT_Y = 0.04f; // The center point of the specific mesh I'm using is not in the right place, so we have to move it 
		mObject3DGlasses.position().y = mGlassesPosition.y - MESH_SHIFT_Y;
		mObject3DGlasses.position().z = -1;
		mObject3DGlasses.scale().x = mObject3DGlasses.scale().y = mObject3DGlasses.scale().z = 0.063f * mGlassesScaleFactor;
	}
	
	@Override
	protected void glSurfaceViewConfig()
    {
		// it is necessary to see the camera preview on the background
	    _glSurfaceView.setEGLConfigChooser( 8, 8, 8, 8, 16, 0 );
	    _glSurfaceView.getHolder().setFormat( PixelFormat.TRANSLUCENT );
    }
	
	//------- Mixing OpenCV CameraView and Min3D View together --------------------------------------------
	@Override
	protected void onCreateSetContentView()
	{
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
                
        //--
		addContentView(_glSurfaceView, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		_glSurfaceView.setZOrderMediaOverlay(true);
	}
	
	//------- OpenCV part --------------------------------------------
	@Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }
	
	@Override
	public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
        
        mPreviewWidth = width;
        mPreviewHeight = height;
        mPreviewShiftFromLeft = (mOpenCvCameraView.getWidth() - width) / 2;
        mPreviewShiftFromTop = (mOpenCvCameraView.getHeight() - height) / 2;
    }

	@Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

	@Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		
		Core.flip( inputFrame.rgba(), mRgba, 1 );
		Core.flip( inputFrame.gray(), mGray, 1 );
		
        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        // Searching for Faces
        MatOfRect faces = new MatOfRect();
        if (mFaceDetector != null)
        {
            mFaceDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, 
            		new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }
        
        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++)
        {
        	// Searching for Eyes
        	MatOfRect eyes = new MatOfRect();
        	if (mEyesDetector != null)
            {
        		mEyesDetector.detectMultiScale(mGray.submat(facesArray[i]), eyes, 1.1, 2, 2, 
                		new Size(mAbsoluteFaceSize, mAbsoluteFaceSize/3), new Size());
            }
        	
        	// Store eyes position
        	Rect[] eyesArray = eyes.toArray();
        	if( eyesArray.length == 1 )
            {
            	Point tl = new Point( facesArray[i].x + eyesArray[0].x, 
            			facesArray[i].y + eyesArray[0].y );
            	
            	Point br = new Point( facesArray[i].x + eyesArray[0].x + eyesArray[0].width, 
            			facesArray[i].y + eyesArray[0].y + eyesArray[0].height );
            	
            	mPositionBetweenEyesOnPreview.x = tl.x + eyesArray[0].width/2;
            	mPositionBetweenEyesOnPreview.y = tl.y + eyesArray[0].height/2;
            	mGlassesScaleFactor = (float) eyesArray[0].width / (float) mPreviewWidth;

            	//--
            	float[] objPosition = new float[4];

            	objPosition = Shared.renderer().ScreenTo3D(mPreviewShiftFromLeft + (int)mPositionBetweenEyesOnPreview.x, 
            			mPreviewShiftFromTop + (int)mPositionBetweenEyesOnPreview.y);
                
            	mGlassesPosition.x = objPosition[0]; 
                mGlassesPosition.y = -objPosition[1];
                
                //--
                //Core.rectangle(mRgba, tl, br, EYES_RECT_COLOR, 3);
                Core.putText(mRgba, "Eyes found.", new Point(400, mPreviewHeight-30), 3, 1, GOOD_NEWS_COLOUR, 2 );
            }
        	
        	//--
            //Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
            Core.putText(mRgba, "Head found.", new Point(50, mPreviewHeight-30), 3, 1, GOOD_NEWS_COLOUR, 2 );
            
            return mRgba;
        }
        
        // If eyes are not found then move glasses away
        Core.putText(mRgba, "Head not found.", new Point(20, mPreviewHeight-30), 3, 1, BAD_NEWS_COLOUR, 2 );
        Core.putText(mRgba, "Eyes not found.", new Point(350, mPreviewHeight-30), 3, 1, BAD_NEWS_COLOUR, 2 );
        mGlassesPosition.x = 100;
        mGlassesPosition.y = 100;
        
        return mRgba;
    }
	
	private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                	// load OpenCV cascade files from application resources
                    try {
                    	File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                    	byte[] buffer = new byte[4096];
                        
                    	// face detector
                    	{
	                        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
	                        mCascadeFaceFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
	                        FileOutputStream os = new FileOutputStream(mCascadeFaceFile);
	                        	                        
	                        int bytesRead;
	                        while ((bytesRead = is.read(buffer)) != -1) {
	                            os.write(buffer, 0, bytesRead);
	                        }
	                        is.close();
	                        os.close();
	
	                        mFaceDetector = new CascadeClassifier(mCascadeFaceFile.getAbsolutePath());
	                        if (mFaceDetector.empty()) {
	                            mFaceDetector = null;
	                        } 
                    	}
                    	
                    	// eyes area detector
                    	{
	                        InputStream is = getResources().openRawResource(R.raw.haarcascade_mcs_eyepair_big);
	                        mCascadeEyesFile = new File(cascadeDir, "haarcascade_mcs_eyepair_big.xml");
	                        FileOutputStream os = new FileOutputStream(mCascadeEyesFile);
	                        
	                        int bytesRead;
	                        while ((bytesRead = is.read(buffer)) != -1) {
	                            os.write(buffer, 0, bytesRead);
	                        }
	                        is.close();
	                        os.close();
	
	                        mEyesDetector = new CascadeClassifier(mCascadeEyesFile.getAbsolutePath());
	                        if (mEyesDetector.empty()) {
	                        	mEyesDetector = null;
	                        } 
                    	}
                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
}
