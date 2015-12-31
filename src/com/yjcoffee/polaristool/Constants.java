package com.yjcoffee.polaristool;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * PolarisTool常量定义
 * 
 * @author Jacky
 */
abstract class Constants {

	static final boolean DEV_MODE = false;
	
	static final String PREF_NAME = "polaris_tool";
	static final String KEY_CAMERA_ORIENTATION = "camera_orientation";
	static final String KEY_DEG_LOCATION_STYLE = "deg_location_style";
	static final String KEY_LONGITUDE = "longitude";
	static final String KEY_MANUAL_LONGITUDE = "manual";
	static final String KEY_GPS_ENABLED = "gps";
	static final String REFER_TIME_ZONE = "GMT+08:00";
	static final String LOCATION_SPLIT = " | ";
	static final String[] DEG_LOCATION_SPLIT = {"°", "′"};
	static final NumberFormat DEC_LOCATION_FORMAT = new DecimalFormat("0.000");
	
	private Constants() {}
}
