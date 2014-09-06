/*
 * Copyright (C) 2013 Open Whisper Systems
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

package com.securecomcode.voice.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.util.Log;
import com.google.thoughtcrimegson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import com.securecomcode.voice.Release;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;
import com.securecomcode.voice.util.PeriodicActionUtils;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * A broadcast receiver that is responsible for scheduling and handling notifications
 * for periodic monitor config update events.
 *
 * @author Stuart O. Anderson
 */

public class MonitorConfigUpdateReceiver extends BroadcastReceiver {
  private static final Gson gson = new Gson();
  @Override
  public void onReceive(Context context, Intent intent) {
    Log.w("MonitorConfigUpdateReceiver", "Initiating scheduled monitor config update...");
    Intent serviceIntent = new Intent(context, MonitorConfigUpdateService.class);
    context.startService(serviceIntent);
  }

  public static void maybeUpdateConfig(Context context) {
    Log.d("MonitorConfigUpdateReceiver", "Updating config now");
    AndroidHttpClient client = AndroidHttpClient.newInstance("RedPhone");
    try {
      String uri = String.format("https://%s/collector/call_quality_questions", Release.DATA_COLLECTION_SERVER_HOST);
      HttpGet getRequest = new HttpGet(uri);

      HttpResponse response = client.execute(getRequest);
      InputStreamReader jsonReader = new InputStreamReader(response.getEntity().getContent());
      CallQualityConfig config = gson.fromJson(jsonReader, CallQualityConfig.class);
      ApplicationPreferencesActivity.setCallQualityConfig(context, config);
    } catch (IOException e) {
      Log.d("MonitorConfigUpdateReceiver", "update failed", e);
    } catch (Exception e) {
      Log.e("MonitorConfigUpdateReceiver", "update error", e);
    } finally {
      client.close();
    }

    PeriodicActionUtils.scheduleUpdate(context, MonitorConfigUpdateReceiver.class);
  }

}
