package be.humanoids.webthingify;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaRecorder;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.iot.webthing.Property;
import org.mozilla.iot.webthing.Thing;

import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

class Phone extends Thing implements SensorEventListener {
    private static final double MAX_AMPLITUDE = 32767.0;
    private static final float DELTA = 0.1f;
    private static final float ALPHA = 0.8f;
    private static final int INTERVAL = 1000;
    private final SensorManager sensorManager;
    private final CameraManager cameraManager;
    private final Vibrator vibrator;
    private Value<Float> brightness;
    private Value<Float> proximity;
    private Value<Float> pressure;
    private Value<Float> humidity;
    private Value<Float> temperature;
    private Value<Float> loudness;
    private Value<Integer> battery;
    private Value<Boolean> charging;
    private Value<Boolean> inMotion;
    private CameraManager.TorchCallback torchCallback = null;
    private MediaRecorder recorder = null;
    private Timer loudnessTimer = null;

    private String cameraId = null;

    private float[] gravity = new float[]{0f, 0f, 0f};

    Phone(
            final String name,
            final SensorManager sensors,
            final BatteryManager batteries,
            final CameraManager cameras,
            final Vibrator vib,
            final boolean canRecordAudio
    ) {
        super(name,
                new JSONArray(Arrays.asList("OnOffSwitch", "Light")),
                "An Android phone"
        );

        sensorManager = sensors;
        cameraManager = cameras;
        vibrator = vib;

        if (canRecordAudio) {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            recorder.setOutputFile("/dev/null");
            loudnessTimer = new Timer();
            try {
                recorder.prepare();
                recorder.start();

                JSONObject loudnessDescription = new JSONObject();
                try {
                    loudnessDescription.put("type", "number");
                    loudnessDescription.put("unit", "decibel");
                    loudnessDescription.put("readOnly", true);
                    loudnessDescription.put("label", "Loudness");
                    loudnessDescription.put("description", "Decibels relative to the maximum amplitude the microphone can measure");
                    loudnessDescription.put("maximum", 0);
                } catch (JSONException e) {
                    Log.e("wt:build", "Failed to build property description", e);
                }

                loudness = new Value<>(0.0f);

                addProperty(new Property<>(this, "loudness", loudness, loudnessDescription));
                loudnessTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        updateLoudness();
                    }
                }, 0, INTERVAL);
            } catch (IOException e) {
                Log.e("wt:build", "Error starting recorder", e);
                recorder.stop();
                recorder.release();
                recorder = null;
            }
        }

        try {
            String[] cams = cameraManager.getCameraIdList();
            for (String cam : cams) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cam);
                try {
                    if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                        cameraId = cam;
                        break;
                    }
                } catch (NullPointerException e) {
                    Log.d("wt:cam", "No characteristics for camera ".concat(cam));
                }
            }
            if (cameraId != null) {
                JSONObject onDescription = new JSONObject();
                try {
                    onDescription.put("@type", "OnOffProperty");
                    onDescription.put("label", "Flashlight On/Off");
                    onDescription.put("type", "boolean");
                    onDescription.put("description", "Whether the flashlight is turned on");
                } catch (JSONException e) {
                    Log.e("wt:build", "Failed to build property description", e);
                }
                final Value<Boolean> on = new Value<>(false, newValue -> {
                    try {
                        Log.i("wt:torch", "Updating torch state to " + (newValue ? "on" : "off"));
                        cameraManager.setTorchMode(cameraId, newValue);
                    } catch (CameraAccessException e) {
                        Log.e("wt:torch", "Could not set torch state", e);
                    }
                });
                torchCallback = new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(@NonNull String camId, boolean enabled) {
                        super.onTorchModeChanged(camId, enabled);
                        if (cameraId.equals(camId) && on.get() != enabled) {
                            Log.i("wt:torch", "Updating property state to " + (enabled ? "on" : "off"));
                            on.updateRemote(enabled);
                        }
                    }
                };
                cameraManager.registerTorchCallback(torchCallback, null);

                addProperty(new Property<>(this, "on", on, onDescription));
            }
        } catch (CameraAccessException e) {
            Log.w("wt:flash", "Error when making flash property", e);
        }

        JSONObject batteryDescription = new JSONObject();
        try {
            batteryDescription.put("@type", "LevelProperty");
            batteryDescription.put("label", "Battery");
            batteryDescription.put("unit", "percent");
            batteryDescription.put("type", "integer");
            batteryDescription.put("description", "Battery charge of the device");
            batteryDescription.put("readOnly", true);
            batteryDescription.put("minimum", 0);
            batteryDescription.put("maximum", 100);
        } catch (JSONException e) {
            Log.e("wt:build", "Failed to build property description", e);
        }

        battery = new Value<>(batteries.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));

        addProperty(new Property<>(this, "battery", battery, batteryDescription));

        JSONObject chargingDescription = new JSONObject();
        try {
            chargingDescription.put("type", "boolean");
            chargingDescription.put("readOnly", true);
            chargingDescription.put("label", "Charging");
            chargingDescription.put("description", "Device is plugged in and charging");
        } catch (JSONException e) {
            Log.e("wt:build", "Failed to build property description", e);
        }

        charging = new Value<>(batteries.isCharging());

        addProperty(new Property<>(this, "charging", charging, chargingDescription));

        Sensor brightnessSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (brightnessSensor != null) {
            JSONObject brightnessDescription = new JSONObject();
            try {
                brightnessDescription.put("type", "number");
                brightnessDescription.put("readOnly", true);
                brightnessDescription.put("label", "Brightness");
                brightnessDescription.put("unit", "lux");
                brightnessDescription.put("minimum", 0);
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            brightness = new Value<>(0.0f);

            addProperty(new Property<>(this, "ambientBrightness", brightness, brightnessDescription));
            sensorManager.registerListener(this, brightnessSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor != null) {
            JSONObject proximityDescription = new JSONObject();
            try {
                proximityDescription.put("type", "number");
                proximityDescription.put("readOnly", true);
                proximityDescription.put("label", "Proximity");
                proximityDescription.put("unit", "centimeter");
                proximityDescription.put("minimum", 0);
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            proximity = new Value<>(0.0f);

            addProperty(new Property<>(this, "proximity", proximity, proximityDescription));
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressureSensor != null) {
            JSONObject pressureDescription = new JSONObject();
            try {
                pressureDescription.put("type", "number");
                pressureDescription.put("readOnly", true);
                pressureDescription.put("unit", "hectopascal");
                pressureDescription.put("label", "Pressure");
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            pressure = new Value<>(0.0f);

            addProperty(new Property<>(this, "pressure", pressure, pressureDescription));
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        if (humiditySensor != null) {
            JSONObject humidityDescription = new JSONObject();
            try {
                humidityDescription.put("@type", "LevelProperty");
                humidityDescription.put("type", "number");
                humidityDescription.put("readOnly", true);
                humidityDescription.put("unit", "percent");
                humidityDescription.put("label", "Humidity");
                humidityDescription.put("minimum", 0);
                humidityDescription.put("maximum", 100);
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            humidity = new Value<>(0.0f);

            addProperty(new Property<>(this, "humidity", humidity, humidityDescription));
            sensorManager.registerListener(this, humiditySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if (temperatureSensor != null) {
            JSONObject temperatureDescription = new JSONObject();
            try {
                temperatureDescription.put("type", "number");
                temperatureDescription.put("readOnly", true);
                temperatureDescription.put("unit", "degree celsius");
                temperatureDescription.put("label", "Temperature");
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            temperature = new Value<>(0.0f);

            addProperty(new Property<>(this, "temperature", temperature, temperatureDescription));
            sensorManager.registerListener(this, temperatureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor inMotionSensor = null;
        Sensor stationarySensor = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            inMotionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MOTION_DETECT);
            stationarySensor = sensorManager.getDefaultSensor(Sensor.TYPE_STATIONARY_DETECT);
        }
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if ((inMotionSensor != null && stationarySensor != null) || accelerometer != null) {
            JSONObject motionDescription = new JSONObject();
            try {
                motionDescription.put("type", "boolean");
                motionDescription.put("readOnly", true);
                motionDescription.put("label", "In motion");
                motionDescription.put("description", "If the device is currently in motion or stationary");
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            inMotion = new Value<>(false);

            addProperty(new Property<>(this, "inMotion", inMotion, motionDescription));
            if (inMotionSensor != null && stationarySensor != null) {
                sensorManager.registerListener(this, inMotionSensor, SensorManager.SENSOR_DELAY_NORMAL);
                sensorManager.registerListener(this, stationarySensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        //TODO add property for camera
        //TODO action to take snapshot

        JSONObject vibrateDescription = new JSONObject();
        try {
            vibrateDescription.put("label", "Vibrate");
        } catch (JSONException e) {
            Log.e("wt:build", "Failed to build property description", e);
        }

        addAvailableAction("vibrate", vibrateDescription, VibrateAction.class);
    }

    void vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        else {
            vibrator.vibrate(500);
        }
    }

    void setCharging(boolean isCharging) {
        charging.setRemote(isCharging);
    }

    void setBattery(int batteryPercentage) {
        battery.setRemote(batteryPercentage);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LIGHT:
                if(event.values[0] != brightness.get()) {
                    brightness.setRemote(event.values[0]);
                }
                break;
            case Sensor.TYPE_PRESSURE:
                if(event.values[0] != brightness.get()) {
                    pressure.setRemote(event.values[0]);
                }
                break;
            case Sensor.TYPE_PROXIMITY:
                if(event.values[0] != proximity.get()) {
                    proximity.setRemote(event.values[0]);
                }
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                if(event.values[0] != humidity.get()) {
                    humidity.setRemote(event.values[0]);
                }
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                if(event.values[0] != temperature.get()) {
                    temperature.setRemote(event.values[0]);
                }
                break;
            case Sensor.TYPE_MOTION_DETECT:
                if(!inMotion.get()) {
                    inMotion.setRemote(true);
                }
                break;
            case Sensor.TYPE_STATIONARY_DETECT:
                if(inMotion.get()) {
                    inMotion.setRemote(false);
                }
                break;
            case Sensor.TYPE_ACCELEROMETER:
                boolean isMoving = false;
                for (int i = 0; i < 3; ++i) {
                    gravity[i] = ALPHA * gravity[i] + (1 - ALPHA) * event.values[i];
                    if (event.values[i] - gravity[i] > DELTA) {
                        isMoving = true;
                    }
                }
                if(isMoving != inMotion.get()) {
                    inMotion.setRemote(isMoving);
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    void onDestroy() {
        sensorManager.unregisterListener(this);
        if (recorder != null) {
            recorder.stop();
            recorder.release();
        }
        if (loudnessTimer != null) {
            loudnessTimer.cancel();
        }
        if (torchCallback != null) {
            cameraManager.unregisterTorchCallback(torchCallback);
        }
    }

    private void updateLoudness() {
        int amplitude = recorder.getMaxAmplitude();
        if (amplitude != 0) {
            double db = 20.0 * Math.log10((double) amplitude / MAX_AMPLITUDE);
            if(db != loudness.get()) {
                loudness.setRemote((float) db);
            }
        }
    }
}
