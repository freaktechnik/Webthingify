package be.humanoids.webthingify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.ServiceCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.androidhiddencamera.HiddenCameraUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class WebthingService extends Service {
    private static final String CHANNEL_ID = "wt:service";
    private static final String STOP_SELF_ACTION = "wt:service:stopself";
    private final IBinder binder = new LocalBinder();
    private BatteryManager batteryManager;
    private Phone phone;
    private BroadcastReceiver batteryReceiver;
    private ServerTask server;
    private PowerManager.WakeLock wakeLock;
    private File targetFile;
    private File frontFile = null;
    private Timer cameraTimer = null;
    private int port;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Objects.equals(intent.getAction(), STOP_SELF_ACTION)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        port = intent.getIntExtra("port", 8088);

        createNotificationChannel();
        Intent stopSelfIntent = new Intent(this, WebthingService.class);
        stopSelfIntent.setAction(STOP_SELF_ACTION);
        NotificationCompat.Action notificationAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_stop,
                "Stop",
                PendingIntent.getService(this, 0, stopSelfIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        ).build();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_service)
                .setContentTitle(getString(R.string.notifTitle))
                .setContentText(getString(R.string.notifContent, port))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setLocalOnly(true)
                .setOngoing(true)
                .addAction(notificationAction)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0, null))
                .build();
        startForeground(1, notification);

        return START_STICKY;
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate() {
        super.onCreate();

        if (server != null) {
            return;
        }
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);

        String deviceName = Build.MODEL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            deviceName = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        }

        boolean canTakePictures = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasFrontFacingCam = HiddenCameraUtils.isFrontCameraAvailable(this);

        File tempDir = getCacheDir();
        try {
            targetFile = File.createTempFile("cam", ".jpg", tempDir);
        } catch (IOException e) {
            Log.e("cam:file", "create rear file", e);
        }

        if (hasFrontFacingCam) {
            try {
                frontFile = File.createTempFile("frontcam", ".jpg", tempDir);
            } catch (IOException e) {
                Log.e("cam:file", "create front file", e);
            }
        }


        phone = new Phone(
                deviceName,
                (SensorManager) getSystemService(SENSOR_SERVICE),
                batteryManager,
                (CameraManager) getSystemService(CAMERA_SERVICE),
                (Vibrator) getSystemService(VIBRATOR_SERVICE),
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                canTakePictures,
                targetFile.getName(),
                hasFrontFacingCam,
                frontFile.getName()
        );

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Objects.equals(intent.getAction(), Intent.ACTION_POWER_CONNECTED)) {
                    phone.setCharging(true);
                } else if (Objects.equals(intent.getAction(), Intent.ACTION_POWER_DISCONNECTED)) {
                    phone.setCharging(false);
                }
                phone.setBattery(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            }
        };
        registerReceiver(batteryReceiver, filter);

        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefsFile), Context.MODE_PRIVATE);
        prefs.edit().putBoolean(getString(R.string.serviceRunning), true).apply();

        if (canTakePictures) {
            cameraTimer = new Timer();
            Context ctx = this;
            cameraTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Intent takePicture = new Intent(ctx, BackCamera.class);
                    takePicture.putExtra("file", targetFile.getAbsolutePath());
                    ctx.startService(takePicture);
                    if (hasFrontFacingCam) {
                        Intent takeFrontPic = new Intent(ctx, FrontCamera.class);
                        takeFrontPic.putExtra("file", frontFile.getAbsolutePath());
                        ctx.startService(takeFrontPic);
                    }
                }
            }, 3000, 15000);
        }

        server = new ServerTask(isRunning -> {
            Log.d("wt:service", isRunning ? "isRunning" : "failed to start");
            if (!isRunning) {
                stopSelf();
            }
        },
                tempDir,
                port);
        server.execute(phone);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Webthingify:Server");
        wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("wt:service", "destroy");
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        server.onDestroy();
        server = null;
        unregisterReceiver(batteryReceiver);
        batteryReceiver = null;
        phone.onDestroy();
        phone = null;
        if (cameraTimer != null) {
            cameraTimer.cancel();
        }
        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefsFile), Context.MODE_PRIVATE);
        prefs.edit().putBoolean(getString(R.string.serviceRunning), false).apply();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.notifChannel), importance);
            channel.setDescription(getString(R.string.notifChannelDesc));
            channel.setImportance(NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public class LocalBinder extends Binder {
        public WebthingService getService() {
            return WebthingService.this;
        }
    }
}
