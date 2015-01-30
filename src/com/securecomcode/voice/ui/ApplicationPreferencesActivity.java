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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gcm.GCMRegistrar;
import com.google.thoughtcrimegson.Gson;

import com.securecomcode.voice.audio.DeviceAudioSettings;
import com.securecomcode.voice.monitor.CallQualityConfig;
import com.securecomcode.voice.R;
import com.securecomcode.voice.Release;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;

/**
 * Preferences menu Activity.
 *
 * Also provides methods for setting and getting application preferences.
 *
 * @author Stuart O. Anderson
 */
//TODO(Stuart Anderson): Consider splitting this into an Activity and a utility class
public class ApplicationPreferencesActivity extends SherlockPreferenceActivity {

  public static final String UI_DEBUG_PREF              	  = "pref_debug_ui";
  public static final String AUDIO_COMPAT_PREF          	  = "pref_audio_compat";
  public static final String AUDIO_SPEAKER_INCALL       	  = "pref_speaker_incall";
  public static final String LOOPBACK_MODE_PREF         	  = "pref_loopback";
  public static final String DEBUG_VIEW_PREF            	  = "pref_debugview";
  public static final String SIMULATE_PACKET_DROPS      	  = "pref_simulate_packet_loss";
  public static final String MINIMIZE_LATENCY           	  = "pref_min_latency";
  public static final String SINGLE_THREAD		        	  = "pref_singlethread";
  public static final String USE_C2DM_LEGACY            	  = "pref_use_c2dm";
  public static final String SIGNALING_METHOD           	  = "pref_signaling_method";
  public static final String AUDIO_TRACK_DES_LEVEL      	  = "pref_audio_track_des_buffer_level";
  public static final String CALL_STREAM_DES_LEVEL      	  = "pref_call_stream_des_buffer_level";
  public static final String ASK_DIAGNOSTIC_REPORTING  	 	  = "pref_ask_diagnostic_reporting";
  public static final String ENABLE_CALL_METRICS_UPLOAD		  = "pref_enable_call_metrics_upload";
  public static final String ENABLE_CALL_QUALITY_DIALOG		  = "pref_enable_call_quality_dialog";
  public static final String OPPORTUNISTIC_UPGRADE_PREF 	  = "pref_prompt_upgrade";
  public static final String CALL_QUALITY_QUESTIONS_PREF 	  = "pref_call_quality_questions";
  public static final String USER_ASKED_FOR_FEEDBACK_OPT_IN	  = "pref_user_asked_to_opt_int_for_feedback";
  public static final String BLUETOOTH_ENABLED                = "pref_bluetooth_enabled";
  public static final String DISABLE_SCREEN_IN_CALL           = "pref_disable_screen_in_call";
  public static final String DISABLE_TRAFFIC_GRAPH_IN_CALL    = "pref_disable_traffic_in_call";
  public static final String RECONNECTING_CALL_PREF           = "pref_reconnecting_call";
  public static final String RECONNECTING__IS_IN_CALL_PREF    = "pref_in_call";
  public static final String CALL_END_BUTTON_PRESSED          = "pref_is_terminate_called";
  public static final String CALL_TIMER_COUNT                 = "pref_call_timer_count";
  public static final String INCOMING_CALL                    = "pref_incoming_call";
  public static final String DIRECT_CONNECTION                = "pref_direct_connection";
  public static final String DIRECT_CONNECTION_ENABLE_PREF    = "pref_direct_connection_enable";
  public static final String SHARED_SECRET                    = "pref_shared_secret";

  private static final Gson gson = new Gson();

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    initializeLegacyPreferencesMigration();
    addPreferencesFromResource(R.xml.preferences);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.ApplicationPreferencesActivity_redphone_settings);

    if(Release.DEBUG) {
      addPreferencesFromResource(R.xml.debug);
    }

    if(this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)){
        getPreferenceScreen().findPreference("pref_disable_screen_in_call").setDefaultValue(true);
    }else{
        getPreferenceScreen().findPreference("pref_disable_screen_in_call").setDefaultValue(false);
    }

  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
    }

    return false;
  }

  private void initializeLegacyPreferencesMigration() {
    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(USE_C2DM_LEGACY, false)) {
      PreferenceManager.getDefaultSharedPreferences(this).edit()
        .putString(SIGNALING_METHOD, "gcm")
        .remove(USE_C2DM_LEGACY)
        .commit();
    }
  }

  public static boolean getPromptUpgradePreference(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(OPPORTUNISTIC_UPGRADE_PREF, true);
  }

  public static boolean getDisableDisplayPreference(Context context){
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DISABLE_SCREEN_IN_CALL, true);
  }

  public static boolean getDisableTrafficGraphPreference(Context context){
      return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DISABLE_TRAFFIC_GRAPH_IN_CALL, true);
  }

  public static void setSignalingMethod(Context context, String value) {
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(SIGNALING_METHOD, value).commit();
  }

  public static void setAudioTrackDesBufferLevel( Context context, int level ) {
    PreferenceManager
    .getDefaultSharedPreferences(context).edit().putInt(AUDIO_TRACK_DES_LEVEL, level).commit();
  }

  public static void setCallStreamDesBufferLevel( Context context, float dynDesFrameDelay ) {
    PreferenceManager
    .getDefaultSharedPreferences(context).edit().putFloat(CALL_STREAM_DES_LEVEL,
                                                          dynDesFrameDelay).commit();
  }

  public static int getAudioTrackDesBufferLevel( Context context ) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getInt(AUDIO_TRACK_DES_LEVEL, 900);
  }

  public static float getCallStreamDesBufferLevel( Context context ) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getFloat(CALL_STREAM_DES_LEVEL, 2.5f);
  }

  public static boolean getAudioModeIncall(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(AUDIO_SPEAKER_INCALL,
                                                            DeviceAudioSettings.useInCallMode() );
  }

  public static boolean getAudioCompatibilityMode(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(AUDIO_COMPAT_PREF, false);
  }

  public static boolean getDebugViewEnabled(Context context) {
    return Release.DEBUG &&
           PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(DEBUG_VIEW_PREF, false);
  }

  public static boolean getLoopbackEnabled(Context context) {
    return Release.DEBUG &&
           PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(LOOPBACK_MODE_PREF, false);
  }

  public static boolean isSimulateDroppedPackets(Context context) {
    return Release.DEBUG &&
           PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(SIMULATE_PACKET_DROPS, false);
  }

  public static boolean isMinimizeLatency(Context context) {
    return PreferenceManager
           .getDefaultSharedPreferences(context).getBoolean(MINIMIZE_LATENCY, false);
  }

  public static boolean isSingleThread(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SINGLE_THREAD, false);
  }


  public static void setAskUserToSendDiagnosticData(Context context, boolean enabled) {
    PreferenceManager
    .getDefaultSharedPreferences(context).edit()
    .putBoolean(ASK_DIAGNOSTIC_REPORTING, enabled).commit();
  }

  public static boolean getAskUserToSendDiagnosticData(Context context ) {
    return PreferenceManager
           .getDefaultSharedPreferences(context)
           .getBoolean(ASK_DIAGNOSTIC_REPORTING, true);
  }

  public static void setCallQualityConfig(Context context, CallQualityConfig callQualityConfig) {
    /*PreferenceManager.getDefaultSharedPreferences(context).edit()
      .putString(CALL_QUALITY_QUESTIONS_PREF, gson.toJson(callQualityConfig)).commit();*/
  }

  public static CallQualityConfig getCallQualityConfig(Context context) {
    String configJson = PreferenceManager.getDefaultSharedPreferences(context).
      getString(CALL_QUALITY_QUESTIONS_PREF, gson.toJson(new CallQualityConfig()));

    return gson.fromJson(configJson, CallQualityConfig.class);
  }
  public static void setUserNotfiedOfCallQualitySettings(Context context,boolean value){
	  PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putBoolean(USER_ASKED_FOR_FEEDBACK_OPT_IN, value)
        .commit();
  }
  public static boolean wasUserNotifedOfCallQaulitySettings(Context context){
	  return PreferenceManager
	           .getDefaultSharedPreferences(context).getBoolean(USER_ASKED_FOR_FEEDBACK_OPT_IN, false);
  }
  public static boolean getMetricsOptInFlag(Context context){
	  return false;/*PreferenceManager
	           .getDefaultSharedPreferences(context).getBoolean(ENABLE_CALL_METRICS_UPLOAD, true);*/
  }
  public static void setMetricsOptInFlag(Context context,boolean value){
	  /*PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putBoolean(ENABLE_CALL_METRICS_UPLOAD, value)
        .commit();*/
  }
  public static boolean getDisplayDialogPreference(Context context){
	  return false;/*PreferenceManager
	           .getDefaultSharedPreferences(context).getBoolean(ENABLE_CALL_QUALITY_DIALOG, true);*/
  }
  public static void setDisplayDialogPreference(Context context,boolean value){
	  /*PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putBoolean(ENABLE_CALL_QUALITY_DIALOG, value)
        .commit();*/
  }
  public static boolean getBluetoothEnabled(Context context) {
    return PreferenceManager
      .getDefaultSharedPreferences(context).getBoolean(BLUETOOTH_ENABLED, false);
  }

  public static void setDisplayReconnectingCallPreference(Context context,boolean value){
    PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putBoolean(RECONNECTING_CALL_PREF, value)
        .commit();
  }
  public static boolean getDisplayReconnecting(Context context) {
      return PreferenceManager
              .getDefaultSharedPreferences(context).getBoolean(RECONNECTING_CALL_PREF, false);
  }

  public static void setInCallStatusPreference(Context context,boolean value){
      PreferenceManager.getDefaultSharedPreferences(context).edit()
              .putBoolean(RECONNECTING__IS_IN_CALL_PREF, value)
              .commit();
  }

  public static boolean getIsInCall(Context context) {
      return PreferenceManager
              .getDefaultSharedPreferences(context).getBoolean(RECONNECTING__IS_IN_CALL_PREF, false);
  }

  public static void setCallScreenEndButtonPressed(Context context,boolean value){
      PreferenceManager.getDefaultSharedPreferences(context).edit()
              .putBoolean(CALL_END_BUTTON_PRESSED, value)
              .commit();
  }

  public static boolean isCallScreenEndButtonPressed(Context context) {
      return PreferenceManager
              .getDefaultSharedPreferences(context).getBoolean(CALL_END_BUTTON_PRESSED, false);
  }

    public static void setCallTimerCount(Context context,long value){
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putLong(CALL_TIMER_COUNT, value)
                .commit();
    }

    public static long getCallTimerCount(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getLong(CALL_TIMER_COUNT, 0);
    }

    public static void setIncomingCall(Context context,boolean value){
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(INCOMING_CALL, value)
                .commit();
    }

    public static boolean getIncomingCall(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(INCOMING_CALL, false);
    }

    public static void setDirectConnection(Context context,boolean value){
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(DIRECT_CONNECTION, value)
                .commit();
    }

    public static boolean isDirectConnection(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DIRECT_CONNECTION, false);
    }

    public static void setDirectConnectionPref(Context context,boolean value){
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(DIRECT_CONNECTION_ENABLE_PREF, value)
                .commit();
    }

    public static boolean getDirectConnectionPref(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DIRECT_CONNECTION_ENABLE_PREF, false);
    }

    public static void setSharedSecretPref(Context context,String value){
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(SHARED_SECRET, value)
                .commit();
    }

    public static String getSharedSecretPref(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SHARED_SECRET, "");
    }

}
