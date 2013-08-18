package com.glasses_3d;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import min3d.core.Object3dContainer;
import min3d.core.RendererActivity;
import min3d.parser.IParser;
import min3d.parser.Parser;
import min3d.vos.Light;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;


public class Min3dActivity extends RendererActivity implements CvCameraViewListener2
{   
	private Object3dContainer object3D;
	
	private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
	private Mat mRgba;
    private Mat mGray;
    private float mRelativeFaceSize = 0.2f;
    private int   mAbsoluteFaceSize = 0;
    private CascadeClassifier mJavaDetector;
    private File mCascadeFile;
    private CameraBridgeViewBase mOpenCvCameraView;
	
  //------- Min3D part --------------------------------------------
	@Override
	public void initScene() {
		
		scene.backgroundColor().setAll(0x00000000);
				
		scene.lights().add(new Light());
		scene.lights().add(new Light());
		Light myLight = new Light();    
		myLight.position.setZ(150); 
		scene.lights().add(myLight);
		
		//IParser parser = Parser.createParser(Parser.Type.OBJ,
		//		getResources(), "com.glasses_3d:raw/face_obj", true);
		IParser parser = Parser.createParser(Parser.Type.OBJ,
				getResources(), "com.glasses_3d:raw/glasses_obj", true);
		parser.parse();

		object3D = parser.getParsedObject();
		object3D.scale().x = object3D.scale().y = object3D.scale().z = 0.5f;
		object3D.rotation().y = 180;
		scene.addChild(object3D);
	}

	@Override
	public void updateScene() {

	}
	
	@Override
	protected void glSurfaceViewConfig()
    {
	    _glSurfaceView.setEGLConfigChooser( 8, 8, 8, 8, 16, 0 );
	    _glSurfaceView.getHolder().setFormat( PixelFormat.TRANSLUCENT );
    }
	
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
    }

	@Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

	@Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        MatOfRect faces = new MatOfRect();

        if (mJavaDetector != null)
        {
            mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, 
            		new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }
            
        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++)
        {
            Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
            
            //glRenderer.setHeadPosition( 
            //		new android.graphics.Point(facesArray[0].x + facesArray[0].width/2, facesArray[0].y + facesArray[0].height/2) );
        }
        return mRgba;
    }
	
	private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            mJavaDetector = null;
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
