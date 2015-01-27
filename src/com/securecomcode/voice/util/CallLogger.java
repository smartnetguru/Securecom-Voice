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
package com.securecomcode.voice.util;

import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog.Calls;

import com.securecomcode.voice.call.CallLogDatabase;
import com.securecomcode.voice.contacts.PersonInfo;

public class CallLogger {
  private static final String TAG = CallLogger.class.getName();
  private static ContentValues getCallLogContentValues(Context context, String number, long timestamp) {
    PersonInfo pi        = PersonInfo.getInstance(context, number);
    ContentValues values = new ContentValues();

    values.put(CallLogDatabase.NUMBER, number);
    values.put(CallLogDatabase.CONTACT_NAME, pi.getName());
    values.put(CallLogDatabase.DATE, System.currentTimeMillis());
    values.put(CallLogDatabase.TYPE, pi.getType() );

    return values;
  }

  private static ContentValues getCallLogContentValues(Context context, String number) {
    return getCallLogContentValues(context, number, System.currentTimeMillis());
  }

  public static void logMissedCall(Context context, String number, long timestamp) {
    ContentValues values = getCallLogContentValues(context, number, timestamp);
    values.put(CallLogDatabase.TYPE, Calls.MISSED_TYPE);
    CallLogDatabase.getInstance(context).setCallLogEntryValues(values);
  }

  public static void logOutgoingCall(Context context, String number) {
    ContentValues values = getCallLogContentValues(context, number);
    values.put(Calls.TYPE, Calls.OUTGOING_TYPE);
    CallLogDatabase.getInstance(context).setCallLogEntryValues(values);
  }

  public static void logIncomingCall(Context context, String number) {
    ContentValues values = getCallLogContentValues(context, number);
    values.put(Calls.TYPE, Calls.INCOMING_TYPE);
    CallLogDatabase.getInstance(context).setCallLogEntryValues(values);
  }
}
