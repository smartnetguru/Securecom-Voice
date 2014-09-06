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

import com.securecomcode.voice.util.Util;

/**
 * A signal that requests a verification code via SMS
 * in order to initiate the account creation process.
 *
 * @author Moxie Marlinspike
 *
 */

public class CreateAccountSignal extends Signal {

private static final String CREATE_ACCOUNT_EMAIL_PATH = "/users/verification/email";
private static final String CREATE_ACCOUNT_SMS_PATH   = "/users/verification/sms";
private static final String CREATE_ACCOUNT_VOICE_PATH = "/users/verification/voice";

  private final boolean voice;
  private final String localNumber;

  public CreateAccountSignal(String localNumber, String password, boolean voice) {
    super(localNumber, password, -1);
    this.voice = voice;
    this.localNumber = localNumber;
  }

  @Override
  protected String getMethod() {
    return "GET";
  }

  @Override
  protected String getLocation() {
    String path = "";
    if (localNumber != null && Util.isValidEmail(localNumber)) {
        path = CREATE_ACCOUNT_EMAIL_PATH;
    } else {
        path = voice ? CREATE_ACCOUNT_VOICE_PATH : CREATE_ACCOUNT_SMS_PATH;
    }

    return path;
  }

  @Override
  protected String getBody() {
    return null;
  }
}
