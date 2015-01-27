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

import android.content.Context;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.securecomcode.voice.R;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;


public class CallTimer extends CountDownTimer {

    private static final Object instanceLock = new Object();
    private static volatile CallTimer instance;
    private static long counter = 0;
    private TextView elapsedTime;
    private GraphView graphView;
    private BarGraphSeries<DataPoint> bgs;
    private TextView status;
    private Context context;
    private static boolean isTimerActive = false;

    public static CallTimer getInstance(Context context, long millisInFuture, long countDownInterval, TextView elapsedTime, GraphView graphView, BarGraphSeries<DataPoint> bgs, TextView status) {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new CallTimer(millisInFuture, countDownInterval);
                    instance.elapsedTime = elapsedTime;
                    instance.graphView = graphView;
                    instance.bgs = bgs;
                    instance.status = status;
                    instance.context = context;
                    counter = ApplicationPreferencesActivity.getCallTimerCount(context);
                }
            }
        }

        return instance;
    }

    private CallTimer(long millisInFuture, long countDownInterval) {
        super(millisInFuture, countDownInterval);
    }

    @Override
    public void onTick(long millisUntilFinished) {
        isTimerActive = true;
        elapsedTime.setText(formatTime(counter));
        counter = counter + 1000;
        if(ApplicationPreferencesActivity.getDisableTrafficGraphPreference(context)) {
            DataPoint[] dataPoints = TrafficMonitor.getInstance(context).getDataPoints();
            graphView.setVisibility(View.VISIBLE);
            bgs.resetData(dataPoints);
        }

        if(ApplicationPreferencesActivity.getDisplayReconnecting(context)){
            status.setText(context.getResources().getString(R.string.RedPhone_reconnecting));
        }else{
            status.setText(context.getResources().getString(R.string.RedPhone_connected));
        }
        ApplicationPreferencesActivity.setCallTimerCount(context, counter);
    }

    @Override
    public void onFinish() {

    }

    public static boolean isTimerActive(){
        return isTimerActive;
    }

    public static void resetTimer(){
        if(instance != null) {
            instance.cancel();
        }
        instance = null;
        isTimerActive = false;
    }

    public String formatTime(long millis) {
        String output;
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours=minutes/ 60;

        seconds = seconds % 60;
        minutes = minutes % 60;
        hours=hours%60;

        String secondsD = String.valueOf(seconds);
        String minutesD = String.valueOf(minutes);
        String hoursD=String.valueOf(hours);


        if (seconds < 10)
            secondsD = "0" + seconds;
        if (minutes < 10)
            minutesD = "0" + minutes;

        if (hours < 10)
            hoursD = "0" + hours;

        if(hoursD.equals("00")){
            output = minutesD + " : " + secondsD;
        }else {
            output = hoursD + " : " + minutesD + " : " + secondsD;
        }
        return output;
    }


}