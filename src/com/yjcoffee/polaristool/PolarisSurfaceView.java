/**
 * 
 */
package com.yjcoffee.polaristool;

import static com.yjcoffee.polaristool.Constants.*;

import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 相机实时取景View类
 * 
 * @author Jacky
 */
public class PolarisSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
	
	private static final int CIRCLE = 360;
	private static final int ADJUST = 90;
	private static final int DEFAULT_CAMERA_ORIENTATION = 90;
	
	SurfaceHolder holder;
	Camera camera;

	int currentCameraOrientation;

	@SuppressWarnings("deprecation")
	public PolarisSurfaceView(Context context) {
		super(context);
		holder = getHolder();
		holder.addCallback(this);
		
		// 已过期的设置，但版本低于3.0的Android还需要
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (camera == null) {
			camera = Camera.open();
			try {
				camera.setPreviewDisplay(holder);
			} catch (IOException e) {
				camera.release();
				camera = null;
				Log.e("PolarisSurfaceView", "IOException occurs while set camera preview display", e);
			}
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		currentCameraOrientation = getPreferedCameraOrientation();

		camera.setDisplayOrientation(currentCameraOrientation);
		camera.startPreview();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (camera != null) {
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}
	
	void focus() {
		Camera.Parameters parameters = camera.getParameters();
		if (Camera.Parameters.FOCUS_MODE_AUTO.equals(parameters.getFocusMode())) {
			camera.autoFocus(null);
		}
	}

	void adjustCameraOrientation() {
		currentCameraOrientation += 
				(currentCameraOrientation + ADJUST < CIRCLE) ? ADJUST : (ADJUST - CIRCLE);
		
		camera.setDisplayOrientation(currentCameraOrientation);
	}
	
	void confirmCameraOrientationAdjust() {
		SharedPreferences preferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
		
		Editor edit = preferences.edit();
		edit.putInt(KEY_CAMERA_ORIENTATION, currentCameraOrientation);
		edit.commit();
	}
	
	private int getPreferedCameraOrientation() {
		SharedPreferences preferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
		
		return preferences.getInt(KEY_CAMERA_ORIENTATION, DEFAULT_CAMERA_ORIENTATION);
	}
}
