/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2014 Securecom
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

package com.securecomcode.voice.directory;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.securecomcode.voice.Constants;
import com.securecomcode.voice.contacts.ContactTokenDetails;
import com.securecomcode.voice.signaling.AccountCreationException;
import com.securecomcode.voice.signaling.DirectoryResponse;
import com.securecomcode.voice.signaling.RateLimitExceededException;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;
import com.securecomcode.voice.util.DirectoryUtil;
import com.securecomcode.voice.util.PeriodicActionUtils;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A broadcast receiver that is responsible for scheduling and handling notifications
 * for periodic directory update events.
 *
 * @author Moxie Marlinspike
 *
 */

public class DirectoryUpdateReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(final Context context, Intent intent) {
    Log.w("DirectoryUpdateReceiver", "Initiating scheduled directory update...");
    final String localNumber = "";
    final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

    if (preferences.getBoolean(Constants.REGISTERED_PREFERENCE, false)) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          try {
            SignalingSocket signalingSocket = new SignalingSocket(context);

              Directory directory = Directory.getInstance(context);
              Set<String> eligibleContactNumbers = null;

              if(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
                  eligibleContactNumbers = directory.getPushEligibleContactNumbers(preferences.getString(("LOCALNUMBER"), localNumber), null);
              }else if(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
                  eligibleContactNumbers = directory.getPushEligibleContactNumbers(preferences.getString(("LOCALNUMBER"), localNumber), PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.COUNTRY_CODE_SELECTED, ""));
              }

              Map<String, String> tokenMap = DirectoryUtil.getDirectoryServerTokenMap(eligibleContactNumbers);
              List<ContactTokenDetails> activeTokens = signalingSocket.retrieveDirectory(tokenMap.keySet());

              if (activeTokens != null) {
                  for (ContactTokenDetails activeToken : activeTokens) {
                      eligibleContactNumbers.remove(tokenMap.get(activeToken.getToken()));
                      activeToken.setNumber(tokenMap.get(activeToken.getToken()));
                  }

                  directory.setNumbers(activeTokens);
              }
   
          } catch (SignalingException se) {
            Log.w("DirectoryUpdateReceiver", se);
          } catch (Exception e) {
            Log.w("DirectoryUpdateReceiver", e);
          }

          return null;
        }
      }.execute();

      PeriodicActionUtils.scheduleUpdate(context, DirectoryUpdateReceiver.class);
    }
  }
}
