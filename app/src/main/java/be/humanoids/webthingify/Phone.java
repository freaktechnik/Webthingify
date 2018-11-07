package be.humanoids.webthingify;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.iot.webthing.Property;
import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.Value;

import java.util.Arrays;

class Phone extends Thing implements SensorEventListener {
    private final SensorManager sensorManager;
    private final CameraManager cameraManager;
    private final Vibrator vibrator;
    private Value<Float> brightness;
    private Value<Float> proximity;
    private Value<Float> pressure;
    private Value<Float> humidity;
    private Value<Float> temperature;
    private Value<Integer> battery;
    private Value<Boolean> charging;
    private CameraManager.TorchCallback torchCallback = null;

    private String cameraId = null;

    Phone(String name, SensorManager sensors, BatteryManager batteries, CameraManager cameras, Vibrator vib) {
        super(name,
                new JSONArray(Arrays.asList("OnOffSwitch", "Light")),
                "An Android phone"
        );

        sensorManager = sensors;
        cameraManager = cameras;
        vibrator = vib;

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

                final Value<Boolean> on = new Value<>(true, newValue -> {
                    try {
                        cameraManager.setTorchMode(cameraId, newValue);
                    } catch (CameraAccessException e) {
                        // e.printStackTrace();
                    }
                });
                torchCallback = new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(@NonNull String camId, boolean enabled) {
                        super.onTorchModeChanged(camId, enabled);
                        if (cameraId.equals(camId)) {
                            on.set(enabled);
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
            batteryDescription.put("type", "number");
            batteryDescription.put("description", "Battery charge of the device");
            batteryDescription.put("readOnly", true);
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
                brightnessDescription.put("@type", "LevelProperty");
                brightnessDescription.put("type", "number");
                brightnessDescription.put("readOnly", true);
                brightnessDescription.put("label", "Brightness");
                brightnessDescription.put("unit", "lux");
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            brightness = new Value<>(0.0f);

            addProperty(new Property<>(this, "brightness", brightness, brightnessDescription));
            sensorManager.registerListener(this, brightnessSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        JSONObject loudnessDescription = new JSONObject();
        try {
            loudnessDescription.put("type", "number");
            loudnessDescription.put("unit", "decibel");
            loudnessDescription.put("readOnly", true);
            loudnessDescription.put("label", "Loudness");
        } catch (JSONException e) {
            Log.e("wt:build", "Failed to build property description", e);
        }

        Value<Float> loudness = new Value<>(0.0f);

        addProperty(new Property<>(this, "loudness", loudness, loudnessDescription));

        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor != null) {
            JSONObject proximityDescription = new JSONObject();
            try {
                proximityDescription.put("type", "number");
                proximityDescription.put("readOnly", true);
                proximityDescription.put("label", "Proximity");
                proximityDescription.put("unit", "centimeter");
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
                temperatureDescription.put("unit", "celsius");
                temperatureDescription.put("label", "Temperature");
            } catch (JSONException e) {
                Log.e("wt:build", "Failed to build property description", e);
            }

            temperature = new Value<>(0.0f);

            addProperty(new Property<>(this, "temperature", temperature, temperatureDescription));
            sensorManager.registerListener(this, temperatureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        //TODO ambient temp?
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
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    void setCharging(boolean isCharging) {
        charging.set(isCharging);
    }

    void setBattery(int batteryPercentage) {
        battery.set(batteryPercentage);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LIGHT:
                brightness.set(event.values[0]);
                break;
            case Sensor.TYPE_PRESSURE:
                pressure.set(event.values[0]);
                break;
            case Sensor.TYPE_PROXIMITY:
                proximity.set(event.values[0]);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                humidity.set(event.values[0]);
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                temperature.set(event.values[0]);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    void onDestroy() {
        sensorManager.unregisterListener(this);
        if (torchCallback != null) {
            cameraManager.unregisterTorchCallback(torchCallback);
        }

    }
}
