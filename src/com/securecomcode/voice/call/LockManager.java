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
package com.securecomcode.voice.call;

import android.app.KeyguardManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import com.securecomcode.voice.Release;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;

/**
 * Maintains wake lock state.
 *
 * @author Stuart O. Anderson
 */
public class LockManager {
  private final PowerManager.WakeLock fullLock;
  private final PowerManager.WakeLock partialLock;
  private final KeyguardManager.KeyguardLock keyGuardLock;
  private final KeyguardManager km;
  private final WifiManager.WifiLock wifiLock;
  private final ProximityLock proximityLock;

  private final AccelerometerListener accelerometerListener;
  private final ProximityListener proximityListener;
  private final boolean wifiLockEnforced;

  private boolean keyguardDisabled;

  private int orientation = AccelerometerListener.ORIENTATION_UNKNOWN;
  private final Context context;

  public enum PhoneState {
    IDLE,
    PROCESSING,  //used when the phone is active but before the user should be alerted.
    INTERACTIVE,
    IN_CALL,
  }

  private enum LockState {
    FULL,
    PARTIAL,
    SLEEP,
    PROXIMITY
  }

  public LockManager(Context context) {
    this.context = context;
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "RedPhone Full");
    partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RedPhone Partial");
    proximityLock = new ProximityLock(pm);

    km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    keyGuardLock = km.newKeyguardLock("RedPhone KeyGuard");

    WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RedPhone Wifi");

    fullLock.setReferenceCounted(false);
    partialLock.setReferenceCounted(false);
    wifiLock.setReferenceCounted(false);

    accelerometerListener = new AccelerometerListener(context, new AccelerometerListener.OrientationListener() {
      @Override
      public void orientationChanged(int newOrientation) {
        orientation = newOrientation;
        Log.d("LockManager", "Orentation Update: " + newOrientation);
        updateInCallLockState();
      }
    });

    proximityListener = new ProximityListener(context);

    wifiLockEnforced = isWifiPowerActiveModeEnabled(context);
  }

  private boolean isWifiPowerActiveModeEnabled(Context context) {
    int wifi_pwr_active_mode = Settings.Secure.getInt(context.getContentResolver(), "wifi_pwr_active_mode", -1);
    Log.d("LockManager", "Wifi Activity Policy: " + wifi_pwr_active_mode);

    if (wifi_pwr_active_mode == 0) {
      return false;
    }

    return true;
  }

  private void updateInCallLockState() {
    if (orientation != AccelerometerListener.ORIENTATION_HORIZONTAL
      && wifiLockEnforced) {
      setLockState(LockState.PROXIMITY);
    } else {
      setLockState(LockState.FULL);
    }
  }

  public void updatePhoneState(PhoneState state) {
    switch(state) {
      case IDLE:
        setLockState(LockState.SLEEP);
        accelerometerListener.enable(false);
        maybeEnableKeyguard();
        break;
      case PROCESSING:
        setLockState(LockState.PARTIAL);
        accelerometerListener.enable(false);
        proximityListener.enable(false);
        maybeEnableKeyguard();
        break;
      case INTERACTIVE:
        setLockState(LockState.FULL);
        accelerometerListener.enable(false);
        proximityListener.enable(false);
        disableKeyguard();
        break;
      case IN_CALL:
        accelerometerListener.enable(true);
        if(ApplicationPreferencesActivity.getDisableDisplayPreference(context)){
            proximityListener.enable(true);
        }
        updateInCallLockState();
        disableKeyguard();
        break;
    }
  }

  private synchronized void setLockState(LockState newState) {
    switch(newState) {
      case FULL:
        fullLock.acquire();
        partialLock.acquire();
        wifiLock.acquire();
        proximityLock.release();
        break;
      case PARTIAL:
        partialLock.acquire();
        wifiLock.acquire();
        fullLock.release();
        proximityLock.release();
        break;
      case SLEEP:
        fullLock.release();
        partialLock.release();
        wifiLock.release();
        proximityLock.release();
        break;
      case PROXIMITY:
        partialLock.acquire();
        proximityLock.acquire();
        wifiLock.acquire();
        fullLock.release();
        break;
      default:
        throw new IllegalArgumentException("Unhandled Mode: " + newState);
    }
    Log.d("LockManager", "Entered Lock State: " + newState);
  }

  private void disableKeyguard() {
    if(keyguardLocked()) {
      keyGuardLock.disableKeyguard();
      keyguardDisabled = true;
    }
  }

  private void maybeEnableKeyguard() {
    if (keyguardDisabled) {
      keyGuardLock.reenableKeyguard();
      keyguardDisabled = false;
    }
  }

  private boolean keyguardLocked() {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      return km.isKeyguardLocked();
    }
    return true;
  }
}
