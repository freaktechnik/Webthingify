package be.humanoids.webthingify;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.HiddenCameraService;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Camera extends HiddenCameraService {
    private String targetFile = null;

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
                .setCameraFacing(CameraFacing.REAR_FACING_CAMERA)
                .setCameraResolution(CameraResolution.MEDIUM_RESOLUTION)
                .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                .build();
        targetFile = intent.getStringExtra("file");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
        }
        startCamera(cameraConfig);
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i("camera", "taking picture");
                takePicture();
            }
        }, 2000L);
        return START_NOT_STICKY;
    }

    @Override
    public void onImageCapture(@NonNull File imageFile) {
        Log.i("camera", "got picture");
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
}
