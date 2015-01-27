/*
 * Copyright (C) 2011 Whisper Systems
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

package com.securecomcode.voice.ui;

import android.content.Context;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.securecomcode.voice.R;
import com.securecomcode.voice.call.CallTimer;
import com.securecomcode.voice.call.TrafficMonitor;
import com.securecomcode.voice.contacts.PersonInfo;

/**
 * The in-call display card.
 *
 * @author Moxie Marlinspike
 */

public class CallCard extends LinearLayout {

    private ImageView photo;
    private TextView name;
    private TextView phoneNumber;
    private TextView label;
    private TextView elapsedTime;
    private TextView status;
    private TextView graphLabel;
    private GraphView graphView;
    private static boolean isTrafficMonitorRunning = false;
    private CallTimer callTimer;
    BarGraphSeries<DataPoint> bgs = null;

    public CallCard(Context context) {
        super(context);
        initialize();
    }

    public CallCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public CallCard(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    public void reset() {
        setPersonInfo(new PersonInfo());
        this.status.setText("");
        isTrafficMonitorRunning = false;
        CallTimer.resetTimer();
        ApplicationPreferencesActivity.setCallTimerCount(getContext(), 0);
        ApplicationPreferencesActivity.setInCallStatusPreference(getContext(), false);
    }

    public void setElapsedTime() {
       if(callTimer == null){
            if(!CallTimer.isTimerActive()) {
                ApplicationPreferencesActivity.setCallTimerCount(getContext(), 0);
            }else{
                CallTimer.resetTimer();
            }
           callTimer = CallTimer.getInstance(getContext(), 24 * 3600000, 1000, elapsedTime, graphView, bgs, status);
           callTimer.start();
        }
    }

    private void setPersonInfo(PersonInfo personInfo) {
        if (personInfo.getImage() == null) this.photo.setImageResource(R.drawable.picture_unknown);
        else this.photo.setImageBitmap(personInfo.getImage());

        this.name.setText(personInfo.getName());
        this.phoneNumber.setText(personInfo.getNumber());
        this.label.setText(personInfo.getLabel());
    }

    public void setCard(PersonInfo personInfo, String status) {
        setPersonInfo(personInfo);
        if(status.equalsIgnoreCase(getResources().getString(R.string.RedPhone_connected))){
            setElapsedTime();
            ApplicationPreferencesActivity.setInCallStatusPreference(getContext(), true);
            if(!isTrafficMonitorRunning) {
                TrafficMonitor.getInstance(getContext()).execute();
                isTrafficMonitorRunning = true;
            }
        }
        if(status.equalsIgnoreCase(getResources().getString(R.string.RedPhone_ending_call))){
            ApplicationPreferencesActivity.setInCallStatusPreference(getContext(), false);
            TrafficMonitor.getInstance(getContext()).doStopTrafficMonitor();
            ApplicationPreferencesActivity.setCallTimerCount(getContext(), 0);
            CallTimer.resetTimer();
            isTrafficMonitorRunning = false;
        }
        this.status.setText(status);
    }

    private void initialize() {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.call_card, this, true);

        this.elapsedTime = (TextView) findViewById(R.id.elapsedTime);
        this.photo = (ImageView) findViewById(R.id.photo);
        this.phoneNumber = (TextView) findViewById(R.id.phoneNumber);
        this.name = (TextView) findViewById(R.id.name);
        this.label = (TextView) findViewById(R.id.label);
        this.status = (TextView) findViewById(R.id.callStateLabel);
        this.graphLabel = (TextView) findViewById(R.id.section_label);
        this.graphView = (GraphView) findViewById(R.id.graph);

        //Prepare graph view
        initGraphView();
    }

    private void initGraphView(){
        graphView.getViewport().setXAxisBoundsManual(true);
        graphView.getViewport().setMinX(0);
        graphView.getViewport().setMaxX(60);
        graphView.getViewport().setYAxisBoundsManual(true);
        graphView.getViewport().setMinY(0);
        graphView.getViewport().setMaxY(40);
        graphView.setTitle("Incoming Traffic");
        graphView.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graphView.getGridLabelRenderer().setHorizontalAxisTitle("Minute");
        graphView.getGridLabelRenderer().setVerticalAxisTitle("Packets");
        bgs = new BarGraphSeries<DataPoint>(TrafficMonitor.getInstance(getContext()).getDataPoints());
        bgs.setSpacing(25);
        graphView.addSeries(bgs);
    }

}
