package com.shu.VideoWatermarking;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
	VideoPlayView playView;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		playView = findViewById(R.id.Player);
		//获取所支持的编码信息的方法
		//可以直接查看手机下 /system/etc/media_codecs.xml 来获得
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
