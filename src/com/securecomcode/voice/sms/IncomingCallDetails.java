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

package com.securecomcode.voice.sms;

/**
 * Tuple helper for encapsulating details of an incoming call, such as the
 * initiator number, remote port, the call's session ID, and
 * the hostname of the whisperswitch.
 *
 * @author Moxie Marlinspike
 *
 */

public class IncomingCallDetails {

  private final String initiator;
  private final int port;
  private final long sessionId;
  private final String host;
  private final int version;
  private final String ip;

  public IncomingCallDetails(String initiator, int port, long sessionId, String host, int version, String ip) {
    this.initiator = initiator;
    this.port      = port;
    this.sessionId = sessionId;
    this.host      = host;
    this.version   = version;
    this.ip        = ip;
  }

  public String getHost() {
    return host;
  }

  public String getInitiator() {
    return initiator;
  }

  public int getPort() {
    return port;
  }

  public long getSessionId() {
    return sessionId;
  }

  public int getVersion() {
    return version;
  }

  public String getIP(){
      return ip;
  }
}
