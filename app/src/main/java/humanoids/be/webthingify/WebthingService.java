package humanoids.be.webthingify;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.Vibrator;

public class WebthingService extends Service implements BroadcastReceiver {
    private final BatteryManager batteryManager;
    private final IntentFilter filter;
    private Phone phone;

    public WebthingService() {
        batteryManager = (BatteryManager)getSystemService(BATTERY_SERVICE);

        phone = new Phone(
            "Put Phone Name Here",
            (SensorManager)getSystemService(SENSOR_SERVICE),
            batteryManager,
            (CameraManager)getSystemService(CAMERA_SERVICE),
            (Vibrator)getSystemService(VIBRATOR_SERVICE)
        );

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
            phone.setCharging(true);
        }
        else if(intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
            phone.setCharging(false);
        }
        phone.setBattery(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
