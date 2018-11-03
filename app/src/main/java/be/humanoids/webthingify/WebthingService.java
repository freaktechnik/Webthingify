package be.humanoids.webthingify;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.Vibrator;

public class WebthingService extends IntentService {
    private BatteryManager batteryManager;
    private IntentFilter filter;
    private Phone phone;
    private BroadcastReceiver batteryReceiver;
    private ServerTask server;
    private PowerManager.WakeLock wakeLock;

    public WebthingService() {
        super("webthing");
    }

    @Override
    public void onCreate() {
        super.onCreate();
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

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
                    phone.setCharging(true);
                } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
                    phone.setCharging(false);
                }
                phone.setBattery(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            }
        };
        registerReceiver(batteryReceiver, filter);

        server = new ServerTask();
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

    }
}
