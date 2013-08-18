package com.glasses_3d;

import min3d.core.Object3dContainer;
import min3d.core.RendererActivity;
import min3d.parser.IParser;
import min3d.parser.Parser;
import min3d.vos.Light;

import android.graphics.PixelFormat;
import android.os.Bundle;

public class Min3dActivity extends RendererActivity 
{   
	private Object3dContainer object3D;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
	}
	
	@Override
	public void initScene() {
		
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
	    _glSurfaceView.setEGLConfigChooser(8,8,8,8, 16, 0);
	    _glSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
    }
}
