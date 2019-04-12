package com.shu.VideoWatermarking;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.support.annotation.NonNull;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;


public class VideoDecodeThread extends Thread {

	private final static String TAG = "VideoDecodeThread";

	private String OUTPUT_DIR;

	private String outputImageFormat = "NV21";
	private String outputDir = Environment.getExternalStorageDirectory() + "/1/";

	private static final boolean VERBOSE = false;

	private LinkedBlockingQueue<byte[]> mQueue;

	private MediaCodec mediaCodec;

	private String path;

	private Thread childThread;

	private Throwable throwable;

	public VideoDecodeThread(String path) {
		this.path = path;
	}

//设置回调
    private Callback callback;

    public interface Callback {
        void callbackMethod(final Bitmap bitmap);
    }
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

	public void decode(String videoFilePath) throws Throwable {

		if (childThread == null) {
			childThread = new Thread(this, "decode");
			childThread.start();
			if (throwable != null) {
				throw throwable;
			}
		}
	}

	@Override
	public void run() {
		try {
			videoDecode();
		} catch (Throwable t) {
			throwable = t;
		}
	}

	public void videoDecode() {
		MediaExtractor mediaExtractor = new MediaExtractor();
		try {
			mediaExtractor.setDataSource(path); // 设置数据源
		} catch (IOException e1) {
			e1.printStackTrace();
		}


		String mimeType = null;
		for (int i = 0; i < mediaExtractor.getTrackCount(); i++) { // 信道总数
			MediaFormat format = mediaExtractor.getTrackFormat(i); // 音频文件信息
			mimeType = format.getString(MediaFormat.KEY_MIME);
			if (mimeType.startsWith("video/")) { // 视频信道
				mediaExtractor.selectTrack(i); // 切换到视频信道
				try {
					mediaCodec = MediaCodec.createDecoderByType(mimeType); // 创建解码器,提供数据输出
					showSupportedColorFormat(mediaCodec.getCodecInfo().getCapabilitiesForType(mimeType));// 查看支持的色彩格式
				} catch (IOException e) {
					e.printStackTrace();
				}

				// 用于临时处理 surfaceView还没有create，却调用configure导致崩溃的问题
//				while (!VideoPlayView.isCreate){
//					try {
//						Thread.sleep(100);
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
//				}

				// RK3288默认输出COLOR_FormatYUV420SemiPlanar(NV12)
                // 实测在RK3288上设置格式没用,永远是COLOR_FormatYUV420SemiPlanar
//				int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
				// 指定解码的帧格式
//				if (isColorFormatSupported(decodeColorFormat, mediaCodec.getCodecInfo().getCapabilitiesForType(mimeType))) {
//					format.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
//					Log.i(TAG, "Set decode color format to type " + decodeColorFormat);
//				} else {
//					Log.i(TAG, "Unable to set decode color format, color format type " + decodeColorFormat + " not supported");
//				}

				mediaCodec.configure(format, null, null, 0);
				break;
			}

		}
		if (mediaCodec == null) {
			Log.e(TAG, "Can't find video info!");
			return;
		}

		mediaCodec.start(); // 启动MediaCodec ，等待传入数据
		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo(); // 用于描述解码得到的byte[]数据的相关信息
		boolean bIsEos = false;
		long startMs = System.currentTimeMillis();


		// ==========开始解码=============
		while (!Thread.interrupted()) {
			if (!bIsEos) {
				int inIndex = mediaCodec.dequeueInputBuffer(0);
				if (inIndex >= 0) {
					ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inIndex);
					int nSampleSize = mediaExtractor.readSampleData(inputBuffer, 0); // 读取一帧数据至buffer中
					if (nSampleSize < 0) {
						Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
						mediaCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						bIsEos = true;
					} else {
						// 填数据
						mediaCodec.queueInputBuffer(inIndex, 0, nSampleSize, mediaExtractor.getSampleTime(), 0); // 通知MediaDecode解码刚刚传入的数据
						mediaExtractor.advance(); // 继续下一取样
					}
				}
			}


			int outIndex = mediaCodec.dequeueOutputBuffer(info, 0);
			switch (outIndex) {
				case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
					Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
					break;
				case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
					Log.d(TAG, "New format " + mediaCodec.getOutputFormat());
					break;
				case MediaCodec.INFO_TRY_AGAIN_LATER:
					Log.d(TAG, "dequeueOutputBuffer timed out!");
					break;
				default:
					ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outIndex);
					Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + outputBuffer);

					// 方法一
                    // 直接读取outputBuffer的内容创建opencv的Mat
                    // 再将其转换为ARGB_8888的Bitmap,耗时不超过50ms
                    // 如果转换为RGB565的Bitmap,耗时不超过30ms

                    long startTestMs = System.currentTimeMillis();
					MediaFormat outputFormat = mediaCodec.getOutputFormat();

					// 为了兼容老机型需要查询是否有crop-left等参数并将其换算为width和height
                    int width = outputFormat.getInteger(MediaFormat.KEY_WIDTH);
                    if (outputFormat.containsKey("crop-left") && outputFormat.containsKey("crop-right")) {
                        width = outputFormat.getInteger("crop-right") + 1 - outputFormat.getInteger("crop-left");
                    }
                    int height = outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    if (outputFormat.containsKey("crop-top") && outputFormat.containsKey("crop-bottom")) {
                        height = outputFormat.getInteger("crop-bottom") + 1 - outputFormat.getInteger("crop-top");
                    }

                    // 这里直接从outputBuffer中读取数据到Mat中
                    // 由于是YUV420格式outputBuffer中总数据量为height * width * 3 / 2
                    // 所以rows参数是height * 3 / 2
                    Mat srcMat = new Mat(height * 3 / 2, width, CvType.CV_8UC1, outputBuffer);
                    Mat dstMat = new Mat();

                    switch (outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)) {
                        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                            Imgproc.cvtColor(srcMat,dstMat,Imgproc.COLOR_YUV2RGB_I420);
                            break;
                        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                            Imgproc.cvtColor(srcMat,dstMat,Imgproc.COLOR_YUV2RGB_NV12);
                            break;
                            default:
                                Log.e(TAG, "Unsupported color format!");
                    }

                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                    Utils.matToBitmap(dstMat,bitmap);
                    long testTime = System.currentTimeMillis() - startTestMs;
                    Log.d(TAG, "testTime: " +  testTime);

                    callback.callbackMethod(bitmap);

                    /*
					// 方法二
					// 可以解析任何格式YUV转化为I420或者NV21
					// 获取每一帧Image图片并转化为YUV
					Image image = mediaCodec.getOutputImage(outIndex);
					outputFrameCount++;
					Log.v(TAG, "Successfully get the "+ outputFrameCount + " output image");

					int width = image.getWidth();
					int height = image.getHeight();
					String fileName;
					switch (outputImageFormat) {
						case "I420":
							fileName = OUTPUT_DIR + String.format(Locale.ENGLISH,"frame_%05d_I420_%dx%d.yuv", outputFrameCount, width, height);
							byte[] data = BitmapUtils.getDataFromImage(image, BitmapUtils.COLOR_FormatI420);
//							dumpFile(fileName, data);
							break;
						case "NV21":
							// 耗时1700ms
							fileName = OUTPUT_DIR + String.format(Locale.ENGLISH,"frame_%05d_NV21_%dx%d.yuv", outputFrameCount, width, height);
							data = BitmapUtils.getDataFromImage(image, BitmapUtils.COLOR_FormatNV21);
//							dumpFile(fileName, data);
							Bitmap bitmap = BitmapUtils.nv21ToBitmap(data,image.getCropRect());
							Bitmap bitmap1 = BitmapUtils.imageToBitmap(image);
							Watermark watermark = new Watermark(bitmap);
							Bitmap bitmap2 = watermark.addWatermark();
							break;
						case "JPEG":
							fileName = OUTPUT_DIR + String.format(Locale.ENGLISH,"frame_%05d.jpg", outputFrameCount);
							compressToJpeg(fileName, image);
							break;
					}
					image.close();
					*/


					//防止视频播放过快
					while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
						try {
							sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
							break;
						}
					}
					mediaCodec.releaseOutputBuffer(outIndex, true);
					break;
			}

			// All decoded frames have been rendered, we can stop playing
			// now
			if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
				break;
			}
		}

		mediaCodec.stop();
		mediaCodec.release();
		mediaExtractor.release();
	}

	private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
		System.out.print("Supported color format: ");
		for (int c : caps.colorFormats) {
			System.out.print(c + "\t");
		}
		System.out.println();
	}

	private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
		for (int c : caps.colorFormats) {
			if (c == colorFormat) {
				return true;
			}
		}
		return false;
	}

	private static void dumpFile(String fileName, byte[] data) {
		FileOutputStream outStream;
		try {
			outStream = new FileOutputStream(fileName);
		} catch (IOException ioe) {
			throw new RuntimeException("Unable to create output file " + fileName, ioe);
		}
		try {
			outStream.write(data);
			outStream.close();
		} catch (IOException ioe) {
			throw new RuntimeException("Failed writing data to file " + fileName, ioe);
		}
	}

	private void compressToJpeg(String fileName, Image image) {
		FileOutputStream outStream;
		try {
			outStream = new FileOutputStream(fileName);
		} catch (IOException ioe) {
			throw new RuntimeException("Unable to create output file " + fileName, ioe);
		}
		Rect rect = image.getCropRect();
		YuvImage yuvImage = new YuvImage(BitmapUtils.getDataFromImage(image, BitmapUtils.COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
		yuvImage.compressToJpeg(rect, 100, outStream);
	}



	private void setOutputDir(String dir) throws IOException {
		File theDir = new File(dir);
		if (!theDir.exists()) {
			theDir.mkdirs();
		} else if (!theDir.isDirectory()) {
			throw new IOException("Not a directory");
		}
		OUTPUT_DIR = theDir.getAbsolutePath() + "/";
	}




}
