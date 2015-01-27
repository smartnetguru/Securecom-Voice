/*
 * Copyright (C) 2015 Securecom
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

import com.jjoe64.graphview.series.DataPoint;
import android.content.Context;
import android.os.AsyncTask;


public class TrafficMonitor extends AsyncTask<Void, Void, Void> {

    private static final Object instanceLock = new Object();
    private static volatile TrafficMonitor instance;
    private Context context;
    private volatile int[] trafficReceived = new int[60];
    private volatile int packets_this_second = 0;
    private int index = 0;
    private volatile DataPoint[] dataPoints = new DataPoint[60];
    private boolean stopTrafficMonitor = false;

    public static TrafficMonitor getInstance(Context context) {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new TrafficMonitor(context);
                }
            }
        }

        return instance;
    }

    private TrafficMonitor(Context context) {
        this.context = context;
    }

    @Override
    protected Void doInBackground(Void... params) {

        while (!stopTrafficMonitor) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (index > trafficReceived.length-1) {
                int[] temp = shiftLeft(trafficReceived, 1);
                temp[temp.length-1] = packets_this_second;
                trafficReceived = temp;
            } else {
                trafficReceived[index] = packets_this_second;
                index++;
            }

            packets_this_second = 0;
        }

        return null;
    }

    static int[] shiftLeft(int[] arr, int shift) {
        int[] tmp = new int[arr.length];
        System.arraycopy(arr, shift, tmp, 0, arr.length-shift);
        System.arraycopy(arr, 0, tmp, arr.length-shift, shift);
        return tmp;
    }


    public void updatePacketCount(){
        this.packets_this_second++;
    }

    public void doStopTrafficMonitor(){
        this.stopTrafficMonitor = true;
        this.cancel(true);
        instance = null;
    }

    public DataPoint[] getDataPoints() {
            for (int i = 0; i <= 59; i++) {
                dataPoints[i] = new DataPoint(i, trafficReceived[i]);
            }
        return dataPoints;
    }
}