/*
 * Copyright (C) 2012 Moxie Marlinspike
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import com.securecomcode.voice.R;
import com.securecomcode.voice.RedPhone;
import com.securecomcode.voice.contacts.PersonInfo;

/**
 * Manages the state of the RedPhone items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class NotificationBarManager {

  private static final int RED_PHONE_NOTIFICATION = 313388;

  public static void setCallEnded(Context context) {
    NotificationManager notificationManager = (NotificationManager)context
        .getSystemService(Context.NOTIFICATION_SERVICE);

    notificationManager.cancel(RED_PHONE_NOTIFICATION);
  }

  public static void setCallInProgress(Context context) {
    NotificationManager notificationManager = (NotificationManager)context
        .getSystemService(Context.NOTIFICATION_SERVICE);

    Intent contentIntent        = new Intent(context, RedPhone.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);
    String notificationText     = context.getString(R.string.NotificationBarManager_redphone_call_in_progress);
    Notification notification   = new Notification(R.drawable.stat_sys_phone_call, null,
                                                   System.currentTimeMillis());

    notification.setLatestEventInfo(context, notificationText, notificationText, pendingIntent);
    notification.flags = Notification.FLAG_NO_CLEAR;
    notificationManager.notify(RED_PHONE_NOTIFICATION, notification);
  }

  public static void notifyMissedCall(Context context, String remoteNumber) {
    Intent intent              = new Intent(DialerActivity.CALL_LOG_ACTION, null,
                                            context, DialerActivity.class);
    PendingIntent launchIntent = PendingIntent.getActivity(context, 0, intent, 0);
    PersonInfo remoteInfo      = PersonInfo.getInstance(context, remoteNumber);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
    builder.setSmallIcon(R.drawable.stat_notify_missed_call);
    builder.setWhen(System.currentTimeMillis());
    builder.setTicker(context
                        .getString(R.string.NotificationBarManager_missed_redphone_call_from_s,
                                   remoteInfo.getName()));
    builder.setContentTitle(context.getString(R.string.NotificationBarManager_missed_redphone_call));
    builder.setContentText(remoteInfo.getName());
    builder.setContentIntent(launchIntent);
    builder.setDefaults(Notification.DEFAULT_VIBRATE);
    builder.setAutoCancel(true);

    NotificationManager manager = (NotificationManager)context
        .getSystemService(Context.NOTIFICATION_SERVICE);

    manager.notify(DialerActivity.MISSED_CALL, builder.build());
  }
}
