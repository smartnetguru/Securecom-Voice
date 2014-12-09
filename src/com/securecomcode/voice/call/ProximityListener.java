/*
*Copyright (C) 2014 Securecom
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.securecomcode.voice.call;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;

public final class ProximityListener {
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private int field = 0x00000020;


 public ProximityListener(Context context) {
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        try {
            field = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
        } catch (Throwable ignored) {
        }
        powerManager = (PowerManager) context.getSystemService(context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(field, "ProximityListener");
    }

    public void enable(boolean enable) {
        synchronized (this) {
            if (enable) {
                mSensorManager.registerListener(mSensorListener, mSensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                if(wakeLock.isHeld()) {
                    wakeLock.release();
                }
                mSensorManager.unregisterListener(mSensorListener);
            }
        }
    }

    SensorEventListener mSensorListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            float distance = event.values[0];
            if(distance < 5){
                if(!wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // ignore
        }
    };

}
