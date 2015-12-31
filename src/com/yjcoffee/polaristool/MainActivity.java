package com.yjcoffee.polaristool;

import static com.yjcoffee.polaristool.Constants.*;
import static com.yjcoffee.polaristool.Constants.DEG_LOCATION_SPLIT;
import static com.yjcoffee.polaristool.Constants.DEV_MODE;
import static com.yjcoffee.polaristool.Constants.KEY_GPS_ENABLED;
import static com.yjcoffee.polaristool.Constants.KEY_LONGITUDE;
import static com.yjcoffee.polaristool.Constants.KEY_MANUAL_LONGITUDE;
import static com.yjcoffee.polaristool.Constants.LOCATION_SPLIT;
import static com.yjcoffee.polaristool.Constants.PREF_NAME;
import static com.yjcoffee.polaristool.Constants.REFER_TIME_ZONE;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.TimeZone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 简易对极轴工具主Activity类
 *  
 * @author Jacky
 */
public class MainActivity extends Activity {

	private static final String TAG_SENSOR = "SENSOR";
	private static NumberFormat CLOCK_FORMAT = new DecimalFormat("00");
	
	private static final int CAMERA_ADJUST_CONFIRM_DELAY = 1500;	// 相机调整确认时间：1.5秒
	private static final int TIMER_INTERVAL = 1000;					// 计算时角间隔：1秒
	private static final int DEFAULT_PROMPT_DELAY = 3000;			// 提示显示时间：3秒
	private static final int LOCATION_UPDATE_INTERVAL = 30000;		// 位置侦听间隔：30秒
	private static final int LOCATION_UPDATE_DISTANCE = 100;		// 位置侦听距离：100米
	
	private static final int HANDLER_PROCESS_TIMER = 1;
	private static final int HANDLER_CAMERA_ADJUST = 2;
	
	// 是否移动模式、手动经度设置、GPS启用
	private boolean moving, manualSetting, gpsEnabled;
	
	private PolarisSurfaceView surfaceView;		// 相机取景器
	private ImageView reticleView, pointView;	// 分划板及指针
	private TextView lblPrompt, lblClock, lblLocation, lblDebug;	// 提示, 时钟, 经纬度 及 调试信息显示
	
	private AlertDialog cameraAdjustDialog;		// 相机方向调整确认对话框
	
	private Matrix pointMatrix = new Matrix();		// 指针变换Matrix
	private Matrix reticleMatrix = new Matrix();	// 分划板变换Matrix
	
	// 分划板(图片)中心、指针(图片)中心、当前分划板中心
	private PointF reticleCenter, pointCenter, currentCenter;
	
	// GPS及位置相关
	private LocationManager locationManager;
	private String locationProvider;
	private Location location;
	
	private float polarisAngle, driftAngle;		// 黄线角度(正下方为0)，手机转动角度
	private double siderealDay = 86164.09056f;	// 恒星日的秒数，百度：23小时56分4.09894秒

	private Calendar referenceTime;				// polaris过中天时间
	private int promptDelay, promptTime;		// 提示显示延时，当前提示时间

	private static Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case HANDLER_PROCESS_TIMER:
				((MainActivity) msg.obj).processTimer();
				break;
				
			case HANDLER_CAMERA_ADJUST:
				((AlertDialog) msg.obj).show();
				break;
				
			default:
				super.handleMessage(msg);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		initContentView();	// 初始化contentView
		
		initLocation();		// 初始化位置并显示提示，注册LocationListener
		initGSensor();		// 初始化G-Sensor并注册SensorEventListener
		
		cameraAdjustDialog = createCameraAdjustDialog();
		
		referenceTime = getPolarisReferenceTime();
		sendTimerProcessMessage();
	}
	
	protected void processTimer() {
		Calendar now = Calendar.getInstance(TimeZone.getTimeZone(REFER_TIME_ZONE));	// 当前时间
		if (DEV_MODE) {
			now = getPolarisValidateTime();
		}
		
		long timeInterval = (now.getTimeInMillis() - referenceTime.getTimeInMillis()) / 1000;
		double days = timeInterval / siderealDay;
		
		if (location != null) {		// 已获取或设置位置(经度)，注：北纬和东经为正，南纬和西经为负！
			// 东经增加，时区时间增加；西经增加，时区时间减小
			// polaris自东向西逆时针转动，Matrix为顺时针转动，故取负值
			polarisAngle = -(float)(location.getLongitude() + (days - Math.floor(days)) * 360);
			
			float a = polarisAngle % 360;				// 先对360度取模(余)
			a = (a > 0) ? (360 - a) : Math.abs(a);		// 逆时针的角度(正下方为0)
			
			// 计算时角(24小时)及时钟(12小时)
			float ha = 24 * a / 360, clock = 12 * (180 - a) / 360;
			clock = (clock < 0) ? clock + 12 : clock;
			
			lblClock.setText("HA=" + formatClock(ha) + ", Clock=" + formatClock(clock));
		}
		
		if (currentCenter == null) {	// 获取suerfaceView大小并初始化中心位置
			Rect drawingRect = new Rect();
			surfaceView.getDrawingRect(drawingRect);
			
			if (drawingRect.right != 0 && drawingRect.bottom != 0) {
				currentCenter = new PointF(drawingRect.right / 2 - reticleCenter.x, drawingRect.bottom / 2 - reticleCenter.y);

				reticleMatrix.postTranslate(currentCenter.x, currentCenter.y);
				reticleView.setImageMatrix(reticleMatrix);
				
				pointMatrix.postTranslate(reticleCenter.x - pointCenter.x, reticleCenter.y - 1);
				pointView.setImageMatrix(pointMatrix);
			}
		}
		
		promptTime += TIMER_INTERVAL;
		if (promptTime > promptDelay) {
			setPrompt("", promptDelay);	// 清除提示
		}
		
		sendTimerProcessMessage();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		
		MenuItem locationStyleItem = menu.findItem(R.id.menu_location_style);
		locationStyleItem.setTitle(getText(isPreferedDegreeLocationStyle() ? 	// 下次显示的Title
				R.string.menu_location_style_dec : R.string.menu_location_style_deg));

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_latitude:
			Intent intent = new Intent(this, LongitudeActivity.class);
			intent.putExtra(KEY_MANUAL_LONGITUDE, manualSetting);
			intent.putExtra(KEY_GPS_ENABLED, gpsEnabled);
			if (locationProvider != null) {
				location = locationManager.getLastKnownLocation(locationProvider);
				if (location != null) {
					intent.putExtra(KEY_LONGITUDE, location.getLongitude());
				}
			}
			
			startActivityForResult(intent, 1);
			break;
			
		case R.id.menu_location_style:
			boolean degreeLocationStyle = !isPreferedDegreeLocationStyle();	// 当前的样式
			SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
			Editor edit = preferences.edit();
			edit.putBoolean(KEY_DEG_LOCATION_STYLE, degreeLocationStyle);
			edit.commit();
			
			setPrompt(item.getTitle());
			showLocation();
			
			item.setTitle(degreeLocationStyle ? 		// 下次显示的Title
					R.string.menu_location_style_dec : R.string.menu_location_style_deg);
			
			break;
			
		case R.id.menu_camera_orientation:
			sendCameraOrientationAdjustMsg();
			break;
			
		case R.id.menu_about:
			Intent abount = new Intent(this, AboutActivity.class);
			startActivityForResult(abount, 1);
			break;
		}
		
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1 && resultCode == 1) {
			manualSetting = data.getExtras().getBoolean(KEY_MANUAL_LONGITUDE);
			if (location == null) {
				location = manualSetting ? new Location("manual") : locationManager.getLastKnownLocation(locationProvider);
			}
			
			location.setLongitude((Double) data.getExtras().get(KEY_LONGITUDE));
			setPrompt(manualSetting ? getText(R.string.use_manual_longitude) : getLongitudePrompt());
			
			showLocation();
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	// 经度为0地区，polaris中天参考时间，如：
	// [2012-12-18 04:59:54 GMT+8, 2012-12-19 04:55:58 GMT+8, ...]
	// TODO: 后续考虑使用设置对高级用户提供输入
	protected Calendar getPolarisReferenceTime() {
		Calendar reference = Calendar.getInstance(TimeZone.getTimeZone(REFER_TIME_ZONE));
		reference.set(Calendar.YEAR, 2012);
		reference.set(Calendar.MONTH, Calendar.DECEMBER);
		reference.set(Calendar.DATE, 18);
		reference.set(Calendar.HOUR_OF_DAY, 4);
		reference.set(Calendar.MINUTE, 59);
		reference.set(Calendar.SECOND, 54);
		
		return reference;
	}
	
	// 当前location对polaris的验证时间，如：
	// [2012-12-18 20:50:50 GMT+8 polaris中天, ...]
	private Calendar getPolarisValidateTime() {
		Calendar validateTime = Calendar.getInstance(TimeZone.getTimeZone(REFER_TIME_ZONE));
		validateTime.set(Calendar.YEAR, 2012);
		validateTime.set(Calendar.MONTH, Calendar.DECEMBER);
		validateTime.set(Calendar.DATE, 18);
		validateTime.set(Calendar.HOUR_OF_DAY, 20);
		validateTime.set(Calendar.MINUTE, 50);
		validateTime.set(Calendar.SECOND, 50);
		
		return validateTime;
	}
	
	private void initContentView() {
		// 相机取景器
		surfaceView = new PolarisSurfaceView(this);
		setContentView(surfaceView);
		
		// 极轴分划板
		reticleView = new ImageView(this);
		reticleView.setImageResource(R.drawable.polaris);
		reticleView.setScaleType(ScaleType.MATRIX);
		reticleView.setOnTouchListener(new PolarisViewMovingOnTouchListener());
		reticleView.setVisibility(View.INVISIBLE);
		addContentView(reticleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		// 指针
		pointView = new ImageView(this);
		pointView.setImageResource(R.drawable.point);
		pointView.setScaleType(ScaleType.MATRIX);
		pointView.setVisibility(View.INVISIBLE);
		addContentView(pointView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		
		// 取分划板及指针图片大小
		Bitmap polarisBmp = BitmapFactory.decodeResource(getResources(), R.drawable.polaris);
		reticleCenter = new PointF(polarisBmp.getWidth() / 2, polarisBmp.getHeight() / 2);
		Bitmap pointBmp = BitmapFactory.decodeResource(getResources(), R.drawable.point);
		pointCenter = new PointF(pointBmp.getWidth()  / 2, pointBmp.getHeight() / 2);
		
		// 容纳以下TextView的Layout
		RelativeLayout layout = new RelativeLayout(this);
		
		// 顶部提示
		lblPrompt = new TextView(this);
		lblPrompt.setId(R.id.lblPrompt);
		lblPrompt.setTextColor(Color.GREEN);
		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		layout.addView(lblPrompt, layoutParams);
		
		// 时角及时钟
		lblClock = new TextView(this);
		lblClock.setId(R.id.lblClock);
		lblClock.setTextColor(Color.YELLOW);
		lblClock.setTextSize(20);
		layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		layoutParams.addRule(RelativeLayout.BELOW, lblPrompt.getId());
		layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
		layout.addView(lblClock, layoutParams);
		
		// 经纬度
		lblLocation = new TextView(this);
		lblLocation.setId(R.id.lblLocation);
		lblLocation.setTextColor(Color.WHITE);
		lblLocation.setTextSize(20);
		layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
		layoutParams.setMargins(0, 0, 0, 40);
		layout.addView(lblLocation, layoutParams);

		if (DEV_MODE) {	// 调试面板
			lblDebug = new TextView(this);
			lblDebug.setTextColor(Color.YELLOW);
			layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			layoutParams.setMargins(0, 0, 0, 0);
			layout.addView(lblDebug, layoutParams);
		}
		
		addContentView(layout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
	}
	
	private void initLocation() {
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		locationProvider = locationManager.getBestProvider(new Criteria(), true);
		if (locationProvider != null) {
			location = locationManager.getLastKnownLocation(locationProvider);
		}
		
		if (location != null) {
			setPrompt(getLongitudePrompt());
			showLocation();
		} else {
			setPrompt(getText(R.string.fail_get_location));
		}
		
		// 侦听位置信息(经纬度变化)  
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 
				LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISTANCE, new PolarisLocationListener());
	}
	
	private void initGSensor() {
		SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor sensor = sensorManager.getDefaultSensor (Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(new PolarisSensorEventListener(), 
				sensor, SensorManager.SENSOR_DELAY_NORMAL);
	}
	
	private AlertDialog createCameraAdjustDialog() {
		return new Builder(MainActivity.this)
			.setMessage(getText(R.string.camera_adjusted))
			.setTitle(getText(R.string.menu_camera_orientation))
			.setPositiveButton(getText(R.string.ok), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					surfaceView.confirmCameraOrientationAdjust();	// 确认并保存调整
					setPrompt(getText(R.string.prompt_camera_orientation_confirmed));
				}
			}).setNegativeButton(getText(R.string.cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					sendCameraOrientationAdjustMsg();				// 继续调整
				}
			}).create();
	}
	
	private void sendTimerProcessMessage() {
		Message msg = new Message();
		msg.what = HANDLER_PROCESS_TIMER;
		msg.obj = this;
		
		handler.sendMessageDelayed(msg, TIMER_INTERVAL);	// 3秒后执行
	}
	
	private void sendCameraOrientationAdjustMsg() {
		surfaceView.adjustCameraOrientation();

		Message msg = new Message();
		msg.what = HANDLER_CAMERA_ADJUST;
		msg.obj = cameraAdjustDialog;
		
		handler.sendMessageDelayed(msg, CAMERA_ADJUST_CONFIRM_DELAY);
	}
	
	private void showLocation() {
		if (location == null) {
			return;
		}
		
		float latitude = (float) location.getLatitude();		// 纬度
		float longitude = (float) location.getLongitude();		// 经度
		
		boolean degreeLocationStyle = isPreferedDegreeLocationStyle();
		lblLocation.setText(new StringBuilder()
			.append(getText(R.string.latitude))
			.append(degreeLocationStyle ? formatClock(latitude, DEG_LOCATION_SPLIT) : DEC_LOCATION_FORMAT.format(latitude))
			.append(LOCATION_SPLIT)
			.append(getText(R.string.longitude))
			.append(degreeLocationStyle ? formatClock(longitude, DEG_LOCATION_SPLIT) : DEC_LOCATION_FORMAT.format(longitude))
			.toString()
		);
	}

	@Override
	public void onBackPressed() {
		new Builder(this)
			.setMessage(getText(R.string.confirm_quit))
			.setTitle(getText(R.string.quit_app))
			.setPositiveButton(getText(R.string.ok), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					exitProgram();
				}
			}).setNegativeButton(getText(R.string.cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			}).create().show();
	}
	
	private CharSequence getLongitudePrompt() {
		return getText(gpsEnabled ? R.string.use_gps_longitude : R.string.use_equip_longitude);
	}
	
	private boolean isPreferedDegreeLocationStyle() {
		SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

		return preferences.getBoolean(KEY_DEG_LOCATION_STYLE, false);
	}
	
	private void setPrompt(CharSequence message) {
		setPrompt(message, DEFAULT_PROMPT_DELAY);
	}
	private void setPrompt(CharSequence message, int delay) {
		lblPrompt.setText(message);
		promptDelay = delay;
		promptTime = 0;
	}
	
	private void exitProgram() {
		super.onBackPressed();
		
		android.os.Process.killProcess(android.os.Process.myPid());
	}
	
	private static String formatClock(float clock, String... split) {
		int hour = (int) Math.floor(clock);
		int min = (int) Math.floor(0.5 + (clock - Math.floor(clock)) * 60);		// 四舍五入
		if (min >= 60) {	// 解决诸如13:60的问题
			min -= 60; 
			if (++hour >= 24) {	// 先加！
				hour -= 24;
			}
		}
		
		if (split == null || split.length == 0) {
			split = new String[] {":", ""};
		}
		
		return CLOCK_FORMAT.format(hour) + split[0] + CLOCK_FORMAT.format(min) + split[1];
	}
	
	// ==================== Listener implementations ===================
	
	private class PolarisSensorEventListener implements SensorEventListener {
		@Override
		public void onSensorChanged(SensorEvent e) {
			float x = (int)(e.values[0] * 100) / 100f;
			float y = (int)(e.values[1] * 100) / 100f;   
			driftAngle = (float)((180 * Math.atan(x / y)) / Math.PI);
			
			if (y < 0) {	// 修正
				if (x > 0) {	// driftAngle < 0
					driftAngle -= 2 * (90 + driftAngle);
				} else {		// driftAngle > 0
					driftAngle += 2 * (90 - driftAngle);
				}
				
				driftAngle = -driftAngle;
			}

			if (DEV_MODE) {
				lblDebug.setText("Sensor: x=" + x + ", y=" + y + ", a=" + driftAngle);
			}
			if (Log.isLoggable(TAG_SENSOR, Log.DEBUG)) {
				Log.d(TAG_SENSOR, "Sensor: x=" + x + ", y=" + y + ", a=" + driftAngle);
			}
			
			if (!moving && currentCenter != null) {		// 未移动且surfaceView已初始化
				float[] values = new float[9];
				reticleMatrix.getValues(values);
				reticleMatrix.setRotate(driftAngle, reticleCenter.x, reticleCenter.y);
				reticleMatrix.postTranslate(currentCenter.x, currentCenter.y);
				reticleView.setImageMatrix(reticleMatrix);
				reticleView.setVisibility(View.VISIBLE);
				
				pointMatrix.getValues(values);
				pointMatrix.setRotate(polarisAngle + driftAngle, pointCenter.x, 0f);
				pointMatrix.postTranslate(reticleCenter.x - pointCenter.x + currentCenter.x, reticleCenter.y - 1 + currentCenter.y);
				pointView.setImageMatrix(pointMatrix);
				pointView.setVisibility(View.VISIBLE);
			}
		}
		
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}	
	}
	
	private class PolarisLocationListener implements LocationListener {
		@Override
		public void onProviderEnabled(String provider) {
			setPrompt(getText(R.string.prompt_gps_enabled));
		}
		
		@Override
		public void onProviderDisabled(String provider) {
			setPrompt(getText(R.string.prompt_gps_disabled));
			gpsEnabled = false;
		}
		
		@Override
		public void onLocationChanged(Location location) {
			if (manualSetting) {
				setPrompt(getText(R.string.prompt_location_changed_manual));
			} else {
				setPrompt(getText(R.string.prompt_location_changed));
				MainActivity.this.location = location;
				gpsEnabled = true;
				showLocation();
			}
		}
		
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {}
	}
	
	private class PolarisViewMovingOnTouchListener implements View.OnTouchListener {
		private PointF startPoint = new PointF();
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction() & MotionEvent.ACTION_MASK) {

			case MotionEvent.ACTION_DOWN:			// 指点杆按下
				startPoint.set(event.getX(), event.getY());	// 当前位子保存为新的起始点
				break;
				
			case MotionEvent.ACTION_MOVE:			// 指点杆保持按下，并进行位移
				if (currentCenter != null) {
					float dx = event.getX() - startPoint.x;
					float dy = event.getY() - startPoint.y;
					
					reticleMatrix.postTranslate(dx, dy);
					pointMatrix.postTranslate(dx, dy);
					
					currentCenter.x += dx;
					currentCenter.y += dy;
					
					startPoint.set(event.getX(), event.getY());	// 将当前坐标保存为新起点
					moving = (dx != 0 || dy != 0);
				}
				break;
				
			case MotionEvent.ACTION_UP:				// 指点杆离开屏幕
				if (moving) {
					surfaceView.focus();
					setPrompt(getText(R.string.camera_focused), 1000);
					moving = false;
				}
				break;

			case MotionEvent.ACTION_POINTER_UP:		// 有手指离开屏幕，但还有手指压住屏幕，就会触发事件
				break;

			case MotionEvent.ACTION_POINTER_DOWN:	// 如果已经有手机压在屏幕上，又有手指压在屏幕上了，多点触摸的意思
				if (locationProvider != null) {
					Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);
					if (lastKnownLocation != null) {
						location = lastKnownLocation;
						setPrompt(getLongitudePrompt());
						
						if (manualSetting) {
							manualSetting = false;
							Toast.makeText(MainActivity.this, 
									getText(R.string.switch_auto_prefix).toString() + getLongitudePrompt(), 
									Toast.LENGTH_SHORT).show();
							
						}
						
						showLocation();
					} else {
						setPrompt(getText(R.string.fail_get_location));
					}
				}
				break;
			}

			reticleView.setImageMatrix(reticleMatrix);
			pointView.setImageMatrix(pointMatrix);

			return true;
       }   
	}
}
