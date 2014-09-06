/*
 * Copyright (C) 2011 Whisper Systems
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.securecomcode.voice.gcm.GCMRegistrarHelper;
import com.securecomcode.voice.util.PeriodicActionUtils;

/**
 * Broadcast receiver that's notified of a boot event,
 * allowing us to schedule a directory update request.
 *
 * @author Moxie Marlinspike
 *
 */

public class DirectoryBootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    PeriodicActionUtils.scheduleUpdate(context, DirectoryUpdateReceiver.class);
    GCMRegistrarHelper.registerClient(context, false);
  }

}
