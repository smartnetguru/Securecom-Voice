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

package com.securecomcode.voice.audio;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import com.securecomcode.voice.R;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;

import java.io.IOException;

/**
 * Handles loading and playing the sequence of sounds we use to indicate call initialization.
 *
 * @author Stuart O. Anderson
 */
public class OutgoingRinger implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener {

  private MediaPlayer mediaPlayer;
  private int currentSoundID;
  private boolean loopEnabled;
  private Context context;

  public OutgoingRinger(Context context) {
    this.context = context;

    loopEnabled = true;
    currentSoundID = -1;

  }

  public void playSonar() {
    start(R.raw.sonarping);
  }

  public void playHandshake() {
    start(R.raw.handshake);
  }

  public void playRing() {
    start(R.raw.outring);
  }

  public void playComplete() {
    stop(R.raw.completed);
  }

  public void playFailure() {
    stop(R.raw.failure);
  }

  public void playBusy() {
    start(R.raw.busy);
  }

  private void setSound( int soundID ) {
    currentSoundID = soundID;
    loopEnabled = true;
  }

  private void start( int soundID ) {
    if( soundID == currentSoundID ) return;
    setSound( soundID );
    start();
  }

  private void start() {
    if( mediaPlayer != null ) mediaPlayer.release();
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
    mediaPlayer.setOnCompletionListener(this);
    mediaPlayer.setOnPreparedListener(this);

    String packageName = context.getPackageName();
    Uri dataUri = Uri.parse("android.resource://"+ packageName +"/"+ currentSoundID );

    try {
      mediaPlayer.setDataSource(context, dataUri);
    } catch (IllegalArgumentException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    } catch (SecurityException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    } catch (IllegalStateException e) {
      e.printStackTrace();
      return;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    }
    try {
      mediaPlayer.prepareAsync();
    } catch (IllegalStateException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    }
  }

  public void stop() {
    if( mediaPlayer == null ) return;
    try {
      mediaPlayer.stop();
    } catch( IllegalStateException e ) {
    }
  }

  private void stop( int soundID ) {
    setSound( soundID );
    loopEnabled = false;
    start();
  }

  public void onCompletion(MediaPlayer mp) {
    //mediaPlayer.release();
    //mediaPlayer = null;
  }

  public void onPrepared(MediaPlayer mp) {
    mediaPlayer.setLooping(loopEnabled);
    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (am.isBluetoothScoAvailableOffCall()
      && ApplicationPreferencesActivity.getBluetoothEnabled(context)) {
      am.startBluetoothSco();
      am.setBluetoothScoOn(true);
    }
    mediaPlayer.start();
  }
}
