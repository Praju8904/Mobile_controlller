package com.prajwal.myfirstapp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener; // Interface to listen for motion
import android.hardware.SensorManager;      // The "Boss" of all sensors
import android.util.Log;

public class SensorHandler implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private ConnectionManager connectionManager;
    private float sensitivity = 15.0f; // Adjust this to change speed


    public void start() {
        // Safety: check if the sensor actually exists on this phone
        if (sensorManager != null && gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI);
            // Note: Used SENSOR_DELAY_UI (slower) to prevent network lag
        } else {
            Log.e("LaserMode", "Gyroscope not found on this device!");
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    public SensorHandler(Context context, ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // axisX = vertical movement (pitch)
            // axisY = horizontal movement (yaw)
            float axisX = event.values[0];
            float axisY = event.values[1];

            if (Math.abs(axisX) > 0.1 || Math.abs(axisY) > 0.1) {
                // We send a specific "GYRO" command to the laptop
                int moveX = (int) (-axisY * sensitivity);
                int moveY = (int) (-axisX * sensitivity);
                connectionManager.sendCommand("MOUSE_MOVE_RELATIVE:" + moveX + ":" + moveY);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}