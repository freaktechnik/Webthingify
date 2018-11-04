package be.humanoids.webthingify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Switch;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Switch toggle = findViewById(R.id.switch1);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.i("wt:checkbox", isChecked ? "y" : "n");
            if (isChecked) {
                startForegroundService(new Intent(this, WebthingService.class));
            } else {
                stopService(new Intent(this, WebthingService.class));
            }
        });
    }
}
