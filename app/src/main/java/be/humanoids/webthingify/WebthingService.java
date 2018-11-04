package be.humanoids.webthingify;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;

import java.util.Objects;

public class WebthingService extends IntentService {
    private static final String CHANNEL_ID = "wt:service";
    private static final String STOP_SELF_ACTION ="wt:service:stopself";
    private BatteryManager batteryManager;
    private Phone phone;
    private BroadcastReceiver batteryReceiver;
    private ServerTask server;
    private PowerManager.WakeLock wakeLock;

    public WebthingService() {
        super("webthing");
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        Intent stopSelfIntent = new Intent(this, WebthingService.class);
        stopSelfIntent.setAction(STOP_SELF_ACTION);
        Notification.Action notificationAction = new Notification.Action.Builder(null, "Stop", PendingIntent.getService(this, 0, stopSelfIntent, PendingIntent.FLAG_CANCEL_CURRENT)).build();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Web thing server")
                .setContentText("Web thing server is running")
                .addAction(notificationAction)
                .build();
        startForeground(1, notification);

        if(server != null) {
            return;
        }
        batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);

        phone = new Phone(
                "Put Phone Name Here",
                (SensorManager) getSystemService(SENSOR_SERVICE),
                batteryManager,
                (CameraManager) getSystemService(CAMERA_SERVICE),
                (Vibrator) getSystemService(VIBRATOR_SERVICE)
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

        server = new ServerTask(isRunning -> {
            if(!isRunning) {
                stopSelf();
            }
        });
        server.execute(phone);
        //TODO stopSelf if server isn't started and turn off switch

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Webthingify:Server");
        wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        if(wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        server.onDestroy();
        server = null;
        unregisterReceiver(batteryReceiver);
        batteryReceiver = null;
        phone.onDestroy();
        phone = null;
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(intent.getAction() == STOP_SELF_ACTION) {
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Web thing service", importance);
            channel.setDescription("Web thing service notifications");
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
