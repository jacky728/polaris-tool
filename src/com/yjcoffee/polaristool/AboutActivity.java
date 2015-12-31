package com.yjcoffee.polaristool;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * 关于面板Activity类
 *  
 * @author Jacky
 */
public class AboutActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		
		TextView version = (TextView) findViewById(R.id.about_project_version);
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			version.setText(getText(R.string.app_ver_prefix) + " " + packageInfo.versionName);
		} catch (NameNotFoundException e) {
			Log.w("ABOUT", "Get App version failed!", e);
			version.setVisibility(View.GONE);
		}
		
		final TextView site = (TextView) findViewById(R.id.abount_forum_site);
		site.getPaint().setFlags(Paint.UNDERLINE_TEXT_FLAG);
		site.setTextColor(Color.BLUE);
		site.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Uri uri = Uri.parse(site.getText().toString());
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				
				startActivity(intent);
			}
		});
	}
}
