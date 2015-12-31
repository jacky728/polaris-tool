package com.yjcoffee.polaristool;

import static com.yjcoffee.polaristool.Constants.KEY_GPS_ENABLED;
import static com.yjcoffee.polaristool.Constants.KEY_LONGITUDE;
import static com.yjcoffee.polaristool.Constants.KEY_MANUAL_LONGITUDE;
import static com.yjcoffee.polaristool.Constants.DEC_LOCATION_FORMAT;
import static com.yjcoffee.polaristool.Constants.PREF_NAME;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

/**
 * 经度设置Activity类
 *  
 * @author Jacky
 */
public class LongitudeActivity extends Activity {
	
	private EditText txtLongitude;
	private RadioGroup eastOrWest;
	private RadioButton eastLongitude, westLongitude;
	private CheckBox chkEquipLongitude;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_longitude);
		
		eastOrWest = (RadioGroup) findViewById(R.id.eastOrWest);
		westLongitude = (RadioButton) findViewById(R.id.westLongitude);
		eastLongitude = (RadioButton) findViewById(R.id.eastLongitude);
		txtLongitude = (EditText) findViewById(R.id.txtLongitude);
		
		Bundle extras = getIntent().getExtras();
		boolean manual = extras.getBoolean(KEY_MANUAL_LONGITUDE);

		chkEquipLongitude = (CheckBox) findViewById(R.id.chkEquipLongitude);
		chkEquipLongitude.setText(getText(extras.getBoolean(KEY_GPS_ENABLED) ? 
				R.string.use_gps_longitude : R.string.use_equip_longitude));
		
		Float equipLongitude = getEquipLongitude();
		if (equipLongitude == null) {
			chkEquipLongitude.setEnabled(false);
			manual = true;
		}
		
		if (manual) {		// 手动设置
			Float prefLongitude = getPreferenceLongitude();
			if (prefLongitude != null) {
				setLongitude(prefLongitude);
			}
		} else {			// 自动设置
			chkEquipLongitude.setChecked(true);
			txtLongitude.setEnabled(false);
			eastLongitude.setEnabled(false);
			westLongitude.setEnabled(false);
			setLongitude(equipLongitude);
		}
		
		chkEquipLongitude.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean useEquipLongitude = chkEquipLongitude.isChecked();
				txtLongitude.setEnabled(!useEquipLongitude);
				eastLongitude.setEnabled(!useEquipLongitude);
				westLongitude.setEnabled(!useEquipLongitude);
				
				if (useEquipLongitude) {
					Float longitude = getEquipLongitude();
					if (longitude != null) {
						setLongitude(longitude);
					} else {
						chkEquipLongitude.setEnabled(false);
					}
				} else {	// use preference saved longitude
					Float prefLongitude = getPreferenceLongitude();
					if (prefLongitude != null) {
						setLongitude(prefLongitude);
					}
					
					txtLongitude.selectAll();
					txtLongitude.requestFocus();
				}
			}
		});
		
		View.OnClickListener listener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (v.getId() == R.id.btnOK) {
					String strLongitude = txtLongitude.getText().toString();
					if (strLongitude.length() == 0) {
						txtLongitude.requestFocus();
						return;
					}
					
					float longitude = Float.parseFloat(strLongitude);
					if (longitude > 180) {
						Toast.makeText(LongitudeActivity.this, getText(R.string.prompt_longitude), Toast.LENGTH_SHORT).show();
						return;
					}
					
					if (westLongitude.isChecked()) {
						longitude = -longitude;
					}
					
					if (!chkEquipLongitude.isChecked()) {
						SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
						Editor edit = preferences.edit();
						edit.putFloat(KEY_LONGITUDE, longitude);
						edit.commit();
					}
					
					Intent data = new Intent();
					data.putExtra(KEY_LONGITUDE, (double)longitude);
					data.putExtra(KEY_MANUAL_LONGITUDE, !chkEquipLongitude.isChecked());
					LongitudeActivity.this.setResult(1, data);
				}
				
				LongitudeActivity.this.finish();
			}
		};
		
		Button btnOK = (Button) findViewById(R.id.btnOK);
		btnOK.setOnClickListener(listener);
		
		Button btnCancel = (Button) findViewById(R.id.btnCancel);
		btnCancel.setOnClickListener(listener);
	}
	
	private void setLongitude(float longitude) {
		txtLongitude.setText(DEC_LOCATION_FORMAT.format(Math.abs(longitude)));
		
		eastOrWest.check((longitude > 0) ? R.id.eastLongitude : R.id.westLongitude);
		eastOrWest.getCheckedRadioButtonId();
	}
	
	private Float getPreferenceLongitude() {
		SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

		float longitude = preferences.getFloat(KEY_LONGITUDE, 0);
		return (longitude != 0) ? longitude : null;
	}
	
	private Float getEquipLongitude() {
		Bundle extras = getIntent().getExtras();
		if (extras != null && !extras.isEmpty()) {
			Object longitude = extras.get(KEY_LONGITUDE);
			if (longitude != null) {
				return (float)((Double) longitude).doubleValue();
			}
		}
		
		return null;
	}
}
