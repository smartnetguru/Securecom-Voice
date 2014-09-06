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

package com.securecomcode.voice.signaling.signals;

import android.content.Context;
import android.preference.PreferenceManager;

import com.securecomcode.voice.Constants;
import com.securecomcode.voice.util.PhoneNumberFormatter;
import com.securecomcode.voice.util.Util;

/**
 * A signal which initiates a call with the specified remote number.
 *
 * @author Moxie Marlinspike
 *
 */

public class InitiateSignal extends Signal {

  private String remoteNumber = "";

  public InitiateSignal(String localNumber, String password, long counter, String remoteNumber) {
    super(localNumber, password, counter);
      if(!Util.isValidEmail(remoteNumber)){
          this.remoteNumber = PhoneNumberFormatter.formatNumber(remoteNumber);
      }else if(Util.isValidEmail(remoteNumber)){
          this.remoteNumber = remoteNumber;
      }
  }

  @Override
  protected String getMethod() {
    return "GET";
  }

  @Override
  protected String getLocation() {
    return "/session/1/" + remoteNumber;
  }

  @Override
  protected String getBody() {
    return null;
  }
}
