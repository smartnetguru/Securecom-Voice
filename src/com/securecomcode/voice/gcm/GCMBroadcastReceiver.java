package com.securecomcode.voice.gcm;

import android.content.Context;

public class GCMBroadcastReceiver extends com.google.android.gcm.GCMBroadcastReceiver {

  @Override
  protected String getGCMIntentServiceClassName(Context context) {
    return "com.securecomcode.voice.gcm.GCMIntentService";
  }

}
