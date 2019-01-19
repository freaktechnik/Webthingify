package be.humanoids.webthingify;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Switch;

import com.androidhiddencamera.HiddenCameraUtils;

import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Switch toggle = findViewById(R.id.switch1);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.i("wt:checkbox", isChecked ? "y" : "n");
            if (isChecked) {
                if (!HiddenCameraUtils.canOverDrawOtherApps(this)) {
                    HiddenCameraUtils.openDrawOverPermissionSetting(this);
                    toggle.setChecked(false);
                    return;
                }
                ArrayList<String> permissionsToRequest = new ArrayList<>();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.CAMERA);
                }
                if (permissionsToRequest.size() > 0) {
                    String[] permissions = new String[permissionsToRequest.size()];
                    requestPermissions(permissionsToRequest.toArray(permissions), 0);
                } else {
                    ContextCompat.startForegroundService(this, new Intent(this, WebthingService.class));
                }
            } else {
                stopService(new Intent(this, WebthingService.class));
            }
        });

        prefsListener = (sharedPreferences, key) -> {
            if (key.equals(getString(R.string.serviceRunning))) {
                toggle.setChecked(sharedPreferences.getBoolean(key, false));
            }
        };
        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefsFile), Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        toggle.setChecked(prefs.getBoolean(getString(R.string.serviceRunning), false));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ContextCompat.startForegroundService(this, new Intent(this, WebthingService.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefsFile), Context.MODE_PRIVATE);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
    }
}
