package com.shu.VideoWatermarking;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
	VideoPlayView playView;
	private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 1;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		playView = findViewById(R.id.Player);

		// 动态权限申请
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);
		}
		//所支持的编码信息的方法可以直接查看手机下 /system/etc/media_codecs.xml 来获得
	}

	@Override
	protected void onResume() {
		super.onResume();
		playView.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		playView.stop();
	}
}
