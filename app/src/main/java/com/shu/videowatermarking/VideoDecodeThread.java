package com.shu.VideoWatermarking;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;


public class VideoDecodeThread extends Thread {

	private final static String TAG = "VideoDecodeThread";

	private String OUTPUT_DIR;

	private String outputImageFormat = "JPEG";
	private String outputDir = Environment.getExternalStorageDirectory() + "/1/";

	private static final int COLOR_FormatI420 = 1;
	private static final int COLOR_FormatNV21 = 2;

	private static final boolean VERBOSE = false;

	private LinkedBlockingQueue<byte[]> mQueue;

	private MediaCodec mediaCodec;

	private Surface surface;

	private String path;

	public VideoDecodeThread(Surface surface, String path) {
		this.surface = surface;
		this.path = path;
	}

	@Override
	public void run() {
		MediaExtractor mediaExtractor = new MediaExtractor();
		try {
			mediaExtractor.setDataSource(path); // 设置数据源
			setOutputDir(outputDir);
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
				while (!VideoPlayView.isCreate){
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

//				int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
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
		int outputFrameCount = 0;

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
//					ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outIndex);
//					Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + outputBuffer);


					// 获取每一帧图片
					Image image = mediaCodec.getOutputImage(outIndex);
					outputFrameCount++;
					Log.v(TAG, "Successfully get the "+ outputFrameCount + " output image");

					ByteBuffer buffer = image.getPlanes()[0].getBuffer();
					byte[] arr = new byte[buffer.remaining()];
					buffer.get(arr);
					if (mQueue != null) {
						try {
							mQueue.put(arr);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					int width = image.getWidth();
					int height = image.getHeight();

					String fileName;
					switch (outputImageFormat) {
						case "I420":
							fileName = OUTPUT_DIR + String.format(Locale.ENGLISH,"frame_%05d_I420_%dx%d.yuv", outputFrameCount, width, height);
							dumpFile(fileName, getDataFromImage(image, COLOR_FormatI420));
							break;
						case "NV21":
							fileName = OUTPUT_DIR + String.format(Locale.ENGLISH,"frame_%05d_NV21_%dx%d.yuv", outputFrameCount, width, height);
							dumpFile(fileName, getDataFromImage(image, COLOR_FormatNV21));
							break;
						case "JPEG":
							fileName = OUTPUT_DIR + String.format(Locale.ENGLISH,"frame_%05d.jpg", outputFrameCount);
							compressToJpeg(fileName, image);
							break;
					}
					image.close();



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
		YuvImage yuvImage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
		yuvImage.compressToJpeg(rect, 100, outStream);
	}

	private static byte[] getDataFromImage(Image image, int colorFormat) {
		if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
			throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
		}
		if (!isImageFormatSupported(image)) {
			throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
		}
		Rect crop = image.getCropRect();
		int format = image.getFormat();
		int width = crop.width();
		int height = crop.height();
		Image.Plane[] planes = image.getPlanes();
		byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
		byte[] rowData = new byte[planes[0].getRowStride()];
		if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
		int channelOffset = 0;
		int outputStride = 1;
		for (int i = 0; i < planes.length; i++) {
			switch (i) {
				case 0:
					channelOffset = 0;
					outputStride = 1;
					break;
				case 1:
					if (colorFormat == COLOR_FormatI420) {
						channelOffset = width * height;
						outputStride = 1;
					} else if (colorFormat == COLOR_FormatNV21) {
						channelOffset = width * height + 1;
						outputStride = 2;
					}
					break;
				case 2:
					if (colorFormat == COLOR_FormatI420) {
						channelOffset = (int) (width * height * 1.25);
						outputStride = 1;
					} else if (colorFormat == COLOR_FormatNV21) {
						channelOffset = width * height;
						outputStride = 2;
					}
					break;
			}
			ByteBuffer buffer = planes[i].getBuffer();
			int rowStride = planes[i].getRowStride();
			int pixelStride = planes[i].getPixelStride();
			if (VERBOSE) {
				Log.v(TAG, "pixelStride " + pixelStride);
				Log.v(TAG, "rowStride " + rowStride);
				Log.v(TAG, "width " + width);
				Log.v(TAG, "height " + height);
				Log.v(TAG, "buffer size " + buffer.remaining());
			}
			int shift = (i == 0) ? 0 : 1;
			int w = width >> shift;
			int h = height >> shift;
			buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
			for (int row = 0; row < h; row++) {
				int length;
				if (pixelStride == 1 && outputStride == 1) {
					length = w;
					buffer.get(data, channelOffset, length);
					channelOffset += length;
				} else {
					length = (w - 1) * pixelStride + 1;
					buffer.get(rowData, 0, length);
					for (int col = 0; col < w; col++) {
						data[channelOffset] = rowData[col * pixelStride];
						channelOffset += outputStride;
					}
				}
				if (row < h - 1) {
					buffer.position(buffer.position() + rowStride - length);
				}
			}
			if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
		}
		return data;
	}

	private static boolean isImageFormatSupported(Image image) {
		int format = image.getFormat();
		switch (format) {
			case ImageFormat.YUV_420_888:
			case ImageFormat.NV21:
			case ImageFormat.YV12:
				return true;
		}
		return false;
	}

	public void setOutputDir(String dir) throws IOException {
		File theDir = new File(dir);
		if (!theDir.exists()) {
			theDir.mkdirs();
		} else if (!theDir.isDirectory()) {
			throw new IOException("Not a directory");
		}
		OUTPUT_DIR = theDir.getAbsolutePath() + "/";
	}

}
