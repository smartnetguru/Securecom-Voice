/*
 * Copyright (C) 2013 Open Whisper Systems
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

package com.securecomcode.voice.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.securecomcode.voice.crypto.zrtp.retained.RetainedSecrets;
import com.securecomcode.voice.util.Base64;
import com.securecomcode.voice.util.PhoneNumberFormatter;
import com.securecomcode.voice.util.Util;

import java.io.IOException;

/**
 * Manages the cache of retained secrets (rs1 and rs2) for each
 * (ZID, phone number) endpoint tuple.
 */
public class RetainedSecretsDatabase {

  private static final String TABLE_NAME = "retained_secrets";
  private static final String ID         = "_id";
  private static final String NUMBER     = "number";
  private static final String ZID        = "zid";
  private static final String EXPIRES    = "expires";
  private static final String RS1        = "rs1";
  private static final String RS2        = "rs2";
  private static final String VERIFIED   = "verified";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " integer PRIMARY KEY, " + NUMBER + " TEXT, " + ZID + " TEXT, " +
      EXPIRES + " INTEGER, " + RS1 + " TEXT, " + RS2 + " TEXT, " + VERIFIED + " INTEGER);";

  public static final String CREATE_INDEX = "CREATE INDEX IF NOT EXISTS cached_secrets_zid_number_index ON " +
      TABLE_NAME + " (" + NUMBER +"," + ZID + ");";

  private final Context context;
  private final SQLiteOpenHelper databaseHelper;

  public RetainedSecretsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    this.context        = context.getApplicationContext();
    this.databaseHelper = databaseHelper;
  }

  public void setVerified(String number, byte[] zid) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
      String encodedNumber = "";

    if(!Util.isValidEmail(number)){
        encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);
    }else if(Util.isValidEmail(number)){
        encodedNumber    = number;
    }

    String encodedZid       = Base64.encodeBytes(zid);

    ContentValues values = new ContentValues();
    values.put(VERIFIED, 1);

    database.update(TABLE_NAME, values, NUMBER + " = ? AND " + ZID + " = ?",
                    new String[] {encodedNumber, encodedZid});
  }

  public boolean isVerified(String number, byte[] zid) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String encodedZid       = Base64.encodeBytes(zid);
      String encodedNumber = "";

    if(!Util.isValidEmail(number)){
        encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);
    }else if(Util.isValidEmail(number)){
        encodedNumber    = number;
    }

    Cursor cursor           = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {VERIFIED},
                              NUMBER + " = ? AND " + ZID + " = ?",
                              new String[] {encodedNumber, encodedZid},
                              null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(VERIFIED)) == 1;
      }

      return false;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void setRetainedSecret(String number, byte[] zid, byte[] rs1, long expiration, boolean continuity) {
    if (System.currentTimeMillis() >= expiration)
      return;

    String encodedNumber = "";

    if(!Util.isValidEmail(number)){
        encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);
    }else if(Util.isValidEmail(number)){
        encodedNumber    = number;
    }

    String encodedZid       = Base64.encodeBytes(zid);
    String encodedRs1       = Base64.encodeBytes(rs1);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    Cursor         cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, null, NUMBER + " = ? AND " + ZID + " = ?",
                              new String[] {encodedNumber, encodedZid}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        long          id     = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        ContentValues values = new ContentValues();

        values.put(RS2, cursor.getString(cursor.getColumnIndexOrThrow(RS1)));
        values.put(RS1, encodedRs1);
        values.put(EXPIRES, expiration);
        if (!continuity) values.put(VERIFIED, 0);

        database.update(TABLE_NAME, values, ID + " = ?", new String[] {id+""});
      } else {
        ContentValues values = new ContentValues();
        values.put(RS1, encodedRs1);
        values.put(RS2, (String)null);
        values.put(ZID, encodedZid);
        values.put(NUMBER, encodedNumber);
        values.put(VERIFIED, false);
        values.put(EXPIRES, expiration);

        database.insert(TABLE_NAME, null, values);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }


  public RetainedSecrets getRetainedSecrets(String number, byte[] zid) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String encodedZid       = Base64.encodeBytes(zid);
    String encodedNumber    = "";
    if(!Util.isValidEmail(number)){
        encodedNumber    = PhoneNumberFormatter.formatNumber(context, number);
    }else if(Util.isValidEmail(number)){
        encodedNumber    = number;
    }


    Cursor cursor = null;

    try {
      cursor = database.query(TABLE_NAME, null, NUMBER + " = ? AND " + ZID + " = ?",
                              new String[] {encodedNumber, encodedZid}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        try {
          long expiration = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES));

          if (System.currentTimeMillis() > expiration)
            continue;

          byte[] rs1 = null;
          byte[] rs2 = null;

          String encodedR1 = cursor.getString(cursor.getColumnIndexOrThrow(RS1));
          String encodedR2 = cursor.getString(cursor.getColumnIndexOrThrow(RS2));

          if (!Util.isEmpty(encodedR1)) rs1 = Base64.decode(encodedR1);
          if (!Util.isEmpty(encodedR2)) rs2 = Base64.decode(encodedR2);

          return new RetainedSecrets(rs1, rs2);
        } catch (IOException e) {
          Log.w("RetainedSecretsDatabase", e);
        }
      }

      return new RetainedSecrets(null, null);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public static void onCreate(SQLiteDatabase db) {
    db.execSQL(CREATE_TABLE);
    db.execSQL(CREATE_INDEX);
  }
}
