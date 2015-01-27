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

package com.securecomcode.voice;

/**
 *
 * Constants for intent passing.
 *
 * @author Moxie Marlinspike
 *
 */

public interface Constants {
  public static final String REMOTE_NUMBER = "remote_number";
  public static final String SESSION       = "session";

  public static final String VERIFYING_PREFERENCE        = "VERIFYING";
  public static final String REGISTERED_PREFERENCE       = "REGISTERED";
  public static final String NUMBER_PREFERENCE           = "Number";
  public static final String PASSWORD_PREFERENCE         = "Password";
  public static final String KEY_PREFERENCE              = "Key";
  public static final String PASSWORD_COUNTER_PREFERENCE = "PasswordCounter";
  public static final String MUTE_VALUE                  = "MuteValue";
  public static final String REG_OPTION_SELECTED         = "reg_option_value";
  public static final String COUNTRY_CODE_SELECTED       = "pref_country_selected";
  public static final String CURRENT_CALL_SESSION_ID     = "current_call_session_id";

}
