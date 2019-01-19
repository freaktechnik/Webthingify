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
import android.widget.EditText;
import android.widget.Switch;

import com.androidhiddencamera.HiddenCameraUtils;

import java.util.ArrayList;

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
                ArrayList<String> permissionsToRequest = new ArrayList<>();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.CAMERA);
                } else if (!HiddenCameraUtils.canOverDrawOtherApps(this)) {
                    HiddenCameraUtils.openDrawOverPermissionSetting(this);
                    toggle.setChecked(false);
                    return;
                }
                if (permissionsToRequest.size() > 0) {
                    String[] permissions = new String[permissionsToRequest.size()];
                    requestPermissions(permissionsToRequest.toArray(permissions), 0);
                } else {
                    startServer();
                }
            } else {
                findViewById(R.id.editText2).setEnabled(true);
                stopService(new Intent(this, WebthingService.class));
            }
        });

        prefsListener = (sharedPreferences, key) -> {
            if (key.equals(getString(R.string.serviceRunning))) {
                boolean isRunning = sharedPreferences.getBoolean(key, false);
                toggle.setChecked(isRunning);
                if (!isRunning) {
                    findViewById(R.id.editText2).setEnabled(true);
                }
            }
        };
        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefsFile), Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        toggle.setChecked(prefs.getBoolean(getString(R.string.serviceRunning), false));
        ((EditText) findViewById(R.id.editText2)).setText(Integer.toString(prefs.getInt(getString(R.string.port_setting), ServerTask.DEFAULT_PORT)));
    }

    private void startServer() {
        EditText port = findViewById(R.id.editText2);
        port.setEnabled(false);
        int portNumber = Integer.valueOf(port.getText().toString());
        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefsFile), Context.MODE_PRIVATE);
        prefs.edit().putInt(getString(R.string.port_setting), portNumber).apply();
        Intent server = new Intent(this, WebthingService.class);
        server.putExtra(getString(R.string.port_setting), portNumber);
        ContextCompat.startForegroundService(this, server);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!HiddenCameraUtils.canOverDrawOtherApps(this)) {
            for (int i = 0; i < permissions.length; ++i) {
                if (permissions[i].equals(Manifest.permission.CAMERA) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    HiddenCameraUtils.openDrawOverPermissionSetting(this);
                    return;
                }
            }
        }
        startServer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefsFile), Context.MODE_PRIVATE);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
    }
}
