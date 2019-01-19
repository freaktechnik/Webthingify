package be.humanoids.webthingify;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.WindowManager;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.HiddenCameraService;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

abstract public class Camera extends HiddenCameraService {
    private String targetFile = null;
    protected int facing;

    public Camera() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        CameraConfig cameraConfig = new CameraConfig()
                .getBuilder(this)
                .setCameraFacing(facing)
                .setCameraResolution(CameraResolution.MEDIUM_RESOLUTION)
                .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                .setImageRotation(getOrientation())
                .build();
        targetFile = intent.getStringExtra("file");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
        }
        startCamera(cameraConfig);
        new android.os.Handler().postDelayed(this::takePicture, 2000L);
        return START_NOT_STICKY;
    }

    @Override
    public void onImageCapture(@NonNull File imageFile) {
        try {
            Path targetPath = Paths.get(targetFile);
            Files.deleteIfExists(targetPath);
            Files.copy(imageFile.toPath(), targetPath);
        } catch (IOException e) {
            Log.e("camera", "saving picture", e);
        }
        stopSelf();
    }

    @Override
    public void onCameraError(int errorCode) {
        Log.e("camera", "got error");
        stopSelf();
    }

    protected int currentRotation() {
        WindowManager windowService = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return windowService.getDefaultDisplay().getRotation();
    }

    abstract protected int getOrientation();
}
