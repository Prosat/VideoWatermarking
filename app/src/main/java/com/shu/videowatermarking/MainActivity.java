package com.shu.VideoWatermarking;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.os.Handler;


import android.os.Message;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements VideoDecodeThread.Callback{
	private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 1;
	private MainActivity self = this;

	private static final String strVideo = Environment.getExternalStorageDirectory().getPath() + "/h264_1080p_60fps.mp4";

	private SoundDecodeThread soundDecodeThread;
	private VideoDecodeThread thread;

	ImageView imgchange;
	Handler myHandler;
	Bitmap bitmap_mark;//用于保存VideoDecodeThread传来的bitmap

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		imgchange = findViewById(R.id.img);

		// 初始化opencv库
		System.loadLibrary("opencv_java4");
		// 动态权限申请
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);
		}
		//所支持的编码信息的方法可以直接查看手机下 /system/etc/media_codecs.xml 来获得

        //用Handler来控制图片切换的速度，现在是10毫秒切换一次，只要小于帧率就可以
		myHandler = new Handler()
		{
			@Override
			//重写handleMessage方法,根据msg中what的值判断是否执行后续操作
			public void handleMessage(Message msg) {
				if(msg.what == 0x123)
				{
					imgchange.setImageBitmap(bitmap_mark);
				}
			}
		};
		//使用定时器,每隔10毫秒让handler发送一个空信息
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				myHandler.sendEmptyMessage(0x123);

			}
		}, 0,10);

        thread = new VideoDecodeThread(strVideo);//开启视频线程
        soundDecodeThread = new SoundDecodeThread(strVideo);//开启音频线程
        soundDecodeThread.start();
        thread.setCallback(self);
        try {
            thread.decode(strVideo);
        } catch (Throwable t) {
            t.printStackTrace();
        }
	}

//VideoDecodeThread回调的程序，将解码生成的bitmap传递给bitmap_mark
	@Override
	public void callbackMethod(final Bitmap bitmap) {
		Log.e("CallBack", "MainActivity类");
		bitmap_mark = bitmap;
	}

}
