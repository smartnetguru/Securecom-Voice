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

package com.securecomcode.voice.signaling.signals;

import com.google.thoughtcrimegson.Gson;

/**
 * A verification signal used to confirm an account creation.
 *
 * @author Moxie Marlinspike
 *
 */

public class VerifyAccountSignal extends Signal {

  private final String challenge;
  private final String key;
  private final String localNumber;

  public VerifyAccountSignal(String localNumber, String password, String challenge, String key) {
    super(localNumber, password, -1);
    this.challenge   = challenge;
    this.key         = key;
    this.localNumber = localNumber;
  }

  @Override
  protected String getMethod() {
    return "PUT";
  }

  @Override
  protected String getLocation() {
    return "/users/verification/" + localNumber;
  }

  @Override
  protected String getBody() {
    Gson gson = new Gson();
    return gson.toJson(new VerifyArguments(challenge, key));
  }

  private static class VerifyArguments {
    public String key;
    public String challenge;

    public VerifyArguments(String challenge, String key) {
      this.key       = key;
      this.challenge = challenge;
    }
  }
}
