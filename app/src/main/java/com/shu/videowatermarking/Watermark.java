package com.shu.VideoWatermarking;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.Image;
import android.support.annotation.FontRes;
import android.support.v4.content.res.FontResourcesParserCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

public class Watermark {
    private Bitmap bitmap;


    public Watermark(Bitmap bitmap){
        this.bitmap = bitmap;
    }


    public Bitmap addWatermark(){
         //  获取原始图片与水印图片的宽与高
        int mBitmapWidth = bitmap.getWidth();
        int mBitmapHeight = bitmap.getHeight();
        Bitmap mNewBitmap = Bitmap.createBitmap(mBitmapWidth, mBitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas mCanvas = new Canvas(mNewBitmap);
        //向位图中开始画入MBitmap原始图片
        mCanvas.drawBitmap(bitmap,0,0,null);
        //添加文字
        Paint mPaint = new Paint();
        String mFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss EEEE").format(new Date());
        mPaint.setColor(Color.RED);
        mPaint.setTextSize(40);
        //水印的位置坐标
        mCanvas.drawText(mFormat, (mBitmapWidth * 1) / 10,(mBitmapHeight*14)/15,mPaint);
        mCanvas.save();
        mCanvas.restore();

        return mNewBitmap;

    }




}
