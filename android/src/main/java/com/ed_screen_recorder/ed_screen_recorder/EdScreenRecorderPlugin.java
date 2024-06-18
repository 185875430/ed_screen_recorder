package com.ed_screen_recorder.ed_screen_recorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.hbisoft.hbrecorder.HBRecorder;
import com.hbisoft.hbrecorder.HBRecorderCodecInfo;
import com.hbisoft.hbrecorder.HBRecorderListener;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import android.media.MediaMetadataRetriever;

/**
 * EdScreenRecorderPlugin
 */
public class EdScreenRecorderPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler,
        HBRecorderListener, PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {

    private FlutterPluginBinding flutterPluginBinding;
    private ActivityPluginBinding activityPluginBinding;
    Result recentResult;
    Result startRecordingResult;
    Result stopRecordingResult;
    Result pauseRecordingResult;
    Result resumeRecordingResult;
    Result screenShotResult;
    Activity activity;
    private static final int SCREEN_RECORD_REQUEST_CODE = 777;
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 666;
    private HBRecorder hbRecorder;
    boolean isAudioEnabled;
    String fileName;
    String dirPathToSave;
    boolean addTimeCode;
    String filePath;
    int videoFrame;
    int videoBitrate;
    String fileOutputFormat;
    String fileExtension;
    boolean success;
    String videoHash;
    long startDate;
    long endDate;
    int width;
    int height;

    boolean micPermission = false;
    boolean mediaPermission = false;

    private void initializeResults() {
        startRecordingResult = null;
        stopRecordingResult = null;
        pauseRecordingResult = null;
        resumeRecordingResult = null;
        recentResult = null;
        screenShotResult = null;
    }

    public static void registerWith(Registrar registrar) {
        final EdScreenRecorderPlugin instance = new EdScreenRecorderPlugin();
        instance.setupChannels(registrar.messenger(), registrar.activity());
        registrar.addActivityResultListener(instance);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.flutterPluginBinding = binding;
        hbRecorder = new HBRecorder(flutterPluginBinding.getApplicationContext(), this);
        HBRecorderCodecInfo hbRecorderCodecInfo = new HBRecorderCodecInfo();

    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        this.flutterPluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activityPluginBinding = binding;
        setupChannels(flutterPluginBinding.getBinaryMessenger(), binding.getActivity());
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        initializeResults();
        recentResult = result;
        switch (call.method) {
            case "startRecordScreen":
                try {
                    startRecordingResult = result;
                    isAudioEnabled = Boolean.TRUE.equals(call.argument("audioenable"));
                    fileName = call.argument("filename");
                    dirPathToSave = call.argument("dirpathtosave");
                    addTimeCode = Boolean.TRUE.equals(call.argument("addtimecode"));
                    videoFrame = call.argument("videoframe");
                    videoBitrate = call.argument("videobitrate");
                    fileOutputFormat = call.argument("fileoutputformat");
                    fileExtension = call.argument("fileextension");
                    videoHash = call.argument("videohash");
                    startDate = call.argument("startdate");
                    width = call.argument("width");
                    height = call.argument("height");
                    customSettings(videoFrame, videoBitrate, fileOutputFormat, addTimeCode, fileName, width, height);
                    if (dirPathToSave != null) {
                        setOutputPath(addTimeCode, fileName, dirPathToSave);
                    }
                    if (isAudioEnabled) {
                        if (ContextCompat.checkSelfPermission(flutterPluginBinding.getApplicationContext(), Manifest.permission.RECORD_AUDIO)
                                != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(activity,
                                    new String[]{Manifest.permission.RECORD_AUDIO},
                                    333);
                        } else {

                            micPermission = true;
                        }
                    } else {
                        micPermission = true;
                    }

                    if (ContextCompat.checkSelfPermission(flutterPluginBinding.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(activity,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                444);
                    } else {
                        mediaPermission = true;
                    }
                    if (micPermission && mediaPermission) {
                        success = startRecordingScreen();
                    }

                } catch (Exception e) {
                    Map<Object, Object> dataMap = new HashMap<Object, Object>();
                    dataMap.put("success", false);
                    dataMap.put("isProgress", false);
                    dataMap.put("file", "");
                    dataMap.put("eventname", "startRecordScreen Error");
                    dataMap.put("message", e.getMessage());
                    dataMap.put("videohash", videoHash);
                    dataMap.put("startdate", startDate);
                    dataMap.put("enddate", endDate);
                    JSONObject jsonObj = new JSONObject(dataMap);
                    startRecordingResult.success(jsonObj.toString());
                    startRecordingResult = null;
                    recentResult = null;
                }
                break;
            case "pauseRecordScreen":
                pauseRecordingResult = result;
                hbRecorder.pauseScreenRecording();
                break;
            case "resumeRecordScreen":
                resumeRecordingResult = result;
                hbRecorder.resumeScreenRecording();
                break;
            case "stopRecordScreen":
                stopRecordingResult = result;
                endDate = call.argument("enddate");
                hbRecorder.stopScreenRecording();
                break;
            case "isAudioEnabled":
                isAudioEnabled = call.argument("enabled");
                hbRecorder.isAudioEnabled(isAudioEnabled);
                result.success(true);
                break;
            case "screenShot":
                screenShotResult = result;
                screenShot();
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d("wyy", "onRequestPermissionsResult:"+requestCode);
        if (requestCode == 333) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                micPermission = true;
            } else {
                micPermission = false;
            }
        } else if (requestCode == 444) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mediaPermission = true;
            } else {
                mediaPermission = false;
            }
        }
        return true;
    }

    Intent data;

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("wyy", "onActivityResult:"+requestCode);
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    this.data = data;
                    hbRecorder.startScreenRecording(data, resultCode);
                }
            }
        }
        if(requestCode == SCREEN_CAPTURE_REQUEST_CODE){
            MediaProjection mediaProjection = ((MediaProjectionManager) Objects.requireNonNull(flutterPluginBinding.getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE))).getMediaProjection(Activity.RESULT_OK, data);
            WindowManager wm1 = activity.getWindowManager();
            int width = wm1.getDefaultDisplay().getWidth();
            int height = wm1.getDefaultDisplay().getHeight();
            Objects.requireNonNull(mediaProjection);
            @SuppressLint("WrongConstant")
            ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 60);
            VirtualDisplay virtualDisplay = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                virtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecordService", width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
            }
            SystemClock.sleep(1000);
            Image image = imageReader.acquireLatestImage();
            if(image!=null){
                Log.d("screenShot", "screenShot: "+image.getWidth()+" "+image.getHeight());
            }
            virtualDisplay.release();
            Bitmap bitmap = image2Bitmap(image);
            if(bitmap!=null){
                String path = writeBitmap(bitmap);
                Log.d("screenShot", "screenShot path: "+path);
                screenShotResult.success(path);
            }else {
                screenShotResult.success("");
            }
            screenShotResult = null;
        }
        return true;
    }

    private void setupChannels(BinaryMessenger messenger, Activity activity) {
        if (activityPluginBinding != null) {
            activityPluginBinding.addActivityResultListener(this);
        }
        this.activity = activity;
        MethodChannel channel = new MethodChannel(messenger, "ed_screen_recorder");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void HBRecorderOnStart() {

        Log.e("Video Start:", "Start called");
        Map<Object, Object> dataMap = new HashMap<Object, Object>();
        dataMap.put("success", success);
        dataMap.put("isProgress", true);
        if (dirPathToSave != null) {
            dataMap.put("file", filePath + "." + fileExtension);
        } else {
            dataMap.put("file", generateFileName(fileName, addTimeCode) + "." + fileExtension);
        }
        dataMap.put("eventname", "startRecordScreen");
        dataMap.put("message", "Started Video");
        dataMap.put("videohash", videoHash);
        dataMap.put("startdate", startDate);
        dataMap.put("enddate", null);
        JSONObject jsonObj = new JSONObject(dataMap);
        if (startRecordingResult != null) {
            startRecordingResult.success(jsonObj.toString());
            startRecordingResult = null;
            recentResult = null;
        }
    }

    @Override
    public void HBRecorderOnComplete() {
        Log.e("Video Complete:", "Complete called");
        Map<Object, Object> dataMap = new HashMap<Object, Object>();
        dataMap.put("success", success);
        dataMap.put("isProgress", false);
        if (dirPathToSave != null) {
            dataMap.put("file", filePath + "." + fileExtension);
        } else {
            dataMap.put("file", generateFileName(fileName, addTimeCode) + "." + fileExtension);
        }
        dataMap.put("eventname", "stopRecordScreen");
        dataMap.put("message", "Paused Video");
        dataMap.put("videohash", videoHash);
        dataMap.put("startdate", startDate);
        dataMap.put("enddate", endDate);
        JSONObject jsonObj = new JSONObject(dataMap);
        try {
            if (stopRecordingResult != null) {
                stopRecordingResult.success(jsonObj.toString());
                stopRecordingResult = null;
                recentResult = null;
            }
        } catch (Exception e) {
            if (recentResult != null) {
                recentResult.error("Error", e.getMessage(), null);
                recentResult = null;
            }
        }
    }

    @Override
    public void HBRecorderOnError(int errorCode, String reason) {
        Log.e("Video Error:", reason);
        if (recentResult != null) {
            recentResult.error("Error", reason, null);
            recentResult = null;
        }
    }

    @Override
    public void HBRecorderOnPause() {
        if (pauseRecordingResult != null) {
            pauseRecordingResult.success(true);
            pauseRecordingResult = null;
            recentResult = null;
        }
    }

    @Override
    public void HBRecorderOnResume() {
        if (resumeRecordingResult != null) {
            resumeRecordingResult.success(true);
            resumeRecordingResult = null;
            recentResult = null;
        }
    }

//    long startTime;
    private Boolean startRecordingScreen() {

        try {
            hbRecorder.enableCustomSettings();
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) flutterPluginBinding
                    .getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            Intent permissionIntent = mediaProjectionManager != null
                    ? mediaProjectionManager.createScreenCaptureIntent()
                    : null;
            activity.startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE);
//            startTime = System.currentTimeMillis();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void customSettings(int videoFrame, int videoBitrate, String fileOutputFormat, boolean addTimeCode,
                                String fileName, int width, int height) {
        hbRecorder.isAudioEnabled(isAudioEnabled);
        hbRecorder.setAudioSource("DEFAULT");
        hbRecorder.setVideoEncoder("DEFAULT");
        hbRecorder.setVideoFrameRate(videoFrame);
        hbRecorder.setVideoBitrate(videoBitrate);
        hbRecorder.setOutputFormat(fileOutputFormat);
        if (width != 0 && height != 0) {
            hbRecorder.setScreenDimensions(height, width);
        }
        if (dirPathToSave == null) {
            hbRecorder.setFileName(generateFileName(fileName, addTimeCode));
        }
    }

    private void setOutputPath(boolean addTimeCode, String fileName, String dirPathToSave) throws IOException {
        hbRecorder.setFileName(generateFileName(fileName, addTimeCode));
        if (dirPathToSave != null && !dirPathToSave.equals("")) {
            File dirFile = new File(dirPathToSave);
            hbRecorder.setOutputPath(dirFile.getAbsolutePath());
            filePath = dirFile.getAbsolutePath() + "/" + generateFileName(fileName, addTimeCode);
        } else {
            hbRecorder.setOutputPath(
                    flutterPluginBinding.getApplicationContext().getExternalCacheDir().getAbsolutePath());
            filePath = flutterPluginBinding.getApplicationContext().getExternalCacheDir().getAbsolutePath() + "/"
                    + generateFileName(fileName, addTimeCode);
        }

    }

    private String generateFileName(String fileName, boolean addTimeCode) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        Date curDate = new Date(System.currentTimeMillis());
        if (addTimeCode) {
            return fileName + "-" + formatter.format(curDate).replace(" ", "");
        } else {
            return fileName;
        }
    }

//    public static final int REQUEST_MEDIA_PROJECTION = 10001;

//    private void screenShot() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) flutterPluginBinding.getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
//            if (mediaProjectionManager != null) {
//                Intent intent = mediaProjectionManager.createScreenCaptureIntent();
//                activity.startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
//            }
//        }
//    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void screenShot() {
        Log.d("screenShot", "screenShot isBusyRecording: " + hbRecorder.isBusyRecording());
        if (!hbRecorder.isBusyRecording()) {
            return;
        }
//        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) flutterPluginBinding
//                .getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
//        Intent permissionIntent = mediaProjectionManager != null
//                ? mediaProjectionManager.createScreenCaptureIntent()
//                : null;
//        activity.startActivityForResult(permissionIntent, SCREEN_CAPTURE_REQUEST_CODE);

        if(android.os.Build.VERSION.SDK_INT >=33){
            //dodo
            screenShotResult.success("");
//            long time= System.currentTimeMillis()-startTime;
//            Log.d("screenShot", "MediaMetadataRetriever:"+filePath+ "." + fileExtension);
//            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//            try {
//                retriever.setDataSource(filePath+ "." + fileExtension);
//
//                SystemClock.sleep(1000);
//                Log.d("screenShot", "MediaMetadataRetriever getFrameAtTime:"+time);
//                Bitmap bitmap = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
//                if (bitmap != null) {
//                    String path = writeBitmap(bitmap);
//                    Log.d("screenShot", "screenShot path: " + path);
//                    screenShotResult.success(path);
//                } else {
//                    screenShotResult.success("");
//                }
//            } catch (IllegalArgumentException e) {
//                Log.d("screenShot", "MediaMetadataRetriever getFrameAtTime error: "+e.getMessage());
//                screenShotResult.success("");
//            }
        }else {
            MediaProjection mediaProjection = ((MediaProjectionManager) Objects.requireNonNull(flutterPluginBinding.getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE))).getMediaProjection(Activity.RESULT_OK, data);
            WindowManager wm1 = activity.getWindowManager();
            int width = wm1.getDefaultDisplay().getWidth();
            int height = wm1.getDefaultDisplay().getHeight();
            Objects.requireNonNull(mediaProjection);
            @SuppressLint("WrongConstant")
            ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 60);
            VirtualDisplay virtualDisplay = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                virtualDisplay = mediaProjection.createVirtualDisplay("screen", width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
            }
            SystemClock.sleep(1000);
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                Log.d("screenShot", "screenShot: " + image.getWidth() + " " + image.getHeight());
            }
            virtualDisplay.release();
            Bitmap bitmap = image2Bitmap(image);
            if (bitmap != null) {
                String path = writeBitmap(bitmap);
                Log.d("screenShot", "screenShot path: " + path);
                screenShotResult.success(path);
            } else {
                screenShotResult.success("");
            }
        }
        screenShotResult = null;

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static Bitmap image2Bitmap(Image image) {
        if (image == null) {
            Log.e("screenShot", "image2Bitmap: image is null");
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        return bitmap;
    }

    private String writeBitmap(Bitmap bitmap) {
        try {
            String path = flutterPluginBinding.getApplicationContext().getCacheDir() + "/"
                    + generateFileName("ScreenShot", true)+".png";
            Log.d("screenShot", "writeBitmap: "+path);
            File imageFile = new File(path);
            FileOutputStream oStream = new FileOutputStream(imageFile);

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, oStream);
            oStream.flush();
            oStream.close();
            return path;
        } catch (Exception ex) {
            Log.e("screenShot", "Error writing bitmap: " + ex.getMessage());
        }
        return null;
    }


}
