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

package com.securecomcode.voice.util;

import android.app.AlertDialog;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.util.Log;

import com.securecomcode.voice.ApplicationContext;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Random utility functions.
 *
 * @author Moxie Marlinspike
 *
 */

public class Util {

  static final String VALID_EMAIL_FORMAT = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?";
  public static final String NUMBER_FORMAT_HELPER = "5555555555";

  public static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ie) {
      throw new AssertionError(ie);
    }
  }

  public static byte[] getBytes(String fromString) {
    try {
      return fromString.getBytes("UTF8");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public static String getString(byte[] fromBytes) {
    try {
      return new String(fromBytes, "UTF8");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public static String getSecret(int size) {
    try {
      byte[] secret = new byte[size];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(secret);
      return Base64.encodeBytes(secret);
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError(nsae);
    }
  }

  public static boolean isEmpty(String value) {
    return (value == null || value.trim().length() == 0);
  }

  public static boolean isEmpty(CharSequence value) {
    return value == null || isEmpty(value.toString());
  }

  public static boolean isEmpty(Editable value) {
    return value == null || isEmpty(value.toString());
  }

  public static void showAlertDialog(Context context, String title, String message) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(context);
    dialog.setTitle(title);
    dialog.setMessage(message);
    dialog.setIcon(android.R.drawable.ic_dialog_alert);
    dialog.setPositiveButton(android.R.string.ok, null);
    dialog.show();
  }

  // XXX-S The consumers of these are way way down in the audio/microphone code.
  // Is it possible to refactor them so that they bubble up their errors in a way
  // that's a little cleaner than reaching back up from all the way down there?
  public static void dieWithError(int msgId) {
    ApplicationContext.getInstance().getCallStateListener().notifyClientError( msgId );
    Log.d("RedPhone:AC", "Dying with error.");
  }

  public static void dieWithError(Exception e) {
    Log.w( "RedPhone:AC", e );
    ApplicationContext.getInstance().getCallStateListener().notifyClientError( e.getMessage() );
  }

  public static String getDeviceE164Number(Context context) {
      String localNumber = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE))
              .getLine1Number();

      if (!isEmpty(localNumber) &&
             !localNumber.startsWith("+")) {
          if (localNumber.length() == 10) localNumber = "+1" + localNumber;
          else localNumber = "+" + localNumber;

          return localNumber;
      }

      return null;
  }

  public static boolean isValidEmail(String email){
      return email.trim().matches(VALID_EMAIL_FORMAT);
  }

  public static boolean isAndroidPhone(){
     return System.getProperty("os.name").equalsIgnoreCase("Linux");
  }

  public static byte[] trim(byte[] input, int length) {
      byte[] result = new byte[length];
      System.arraycopy(input, 0, result, 0, result.length);

      return result;
  }

}

