package com.shu.VideoWatermarking;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.*;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity implements VideoDecodeThread.Callback{

	private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 1;

	private static final String strVideo = Environment.getExternalStorageDirectory().getPath() + "/h264_1080p_60fps.mp4";

	private SoundDecodeThread soundDecodeThread;
	private VideoDecodeThread videoDecodeThread;

	private ImageView imageView;

	private final Handler handler = new Handler()
	{
		@Override
		public void handleMessage(Message msg) {
			Bitmap bitmap = (Bitmap)msg.obj;
			imageView.setImageBitmap(bitmap);
		}
	};


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		imageView = findViewById(R.id.img);

		// 初始化opencv库
		System.loadLibrary("opencv_java4");
		// 动态权限申请
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);
		}
		// 所支持的编码信息的方法可以直接查看手机下 /system/etc/media_codecs.xml 来获得

        hideStatusNavigationBar();
	}

	@Override
	protected void onPause() {
		super.onPause();
		soundDecodeThread.interrupt();
		videoDecodeThread.interrupt();
	}

	@Override
	protected void onResume() {
		super.onResume();
		videoDecodeThread = new VideoDecodeThread(strVideo);// 开启视频线程
		videoDecodeThread.setCallback(this);
		videoDecodeThread.start();

		soundDecodeThread = new SoundDecodeThread(strVideo);// 开启音频线程
		soundDecodeThread.start();
	}

    private void hideStatusNavigationBar(){
		int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                     | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                     | View.SYSTEM_UI_FLAG_FULLSCREEN //hide statusBar
                     | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION; //hide navigationBar
		getWindow().getDecorView().setSystemUiVisibility(uiFlags);
    }

	// VideoDecodeThread回调的程序，将解码生成的bitmap传递给主线程
	@Override
	public void onDecodeFrame(final Bitmap bitmap) {
		Message msg = handler.obtainMessage();
		msg.obj = bitmap;
		handler.sendMessage(msg);
	}

}
