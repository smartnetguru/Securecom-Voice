/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2015 Securecom
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
package com.securecomcode.voice.directory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;

import com.securecomcode.voice.contacts.ContactTokenDetails;
import com.securecomcode.voice.util.InvalidNumberException;
import com.securecomcode.voice.util.PhoneNumberFormatter;
import com.securecomcode.voice.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Directory {

    private static final int INTRODUCED_CHANGE_FROM_TOKEN_TO_E164_NUMBER = 2;

    private static final String DATABASE_NAME    = "whisper_directory.db";
    private static final int    DATABASE_VERSION = 2;

    private static final String TABLE_NAME   = "directory";
    private static final String ID           = "_id";
    private static final String NUMBER       = "number";

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY, " +
            NUMBER       + " TEXT UNIQUE );";

    private static final Object instanceLock = new Object();
    private static volatile Directory instance;

    public static Directory getInstance(Context context) {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new Directory(context);
                }
            }
        }

        return instance;
    }

    private final DatabaseHelper databaseHelper;
    private final Context        context;

    private Directory(Context context) {
        this.context = context;
        this.databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void setNumbers(List<ContactTokenDetails> activeTokens) {
        long timestamp    = System.currentTimeMillis();
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.beginTransaction();

        ContentValues values = new ContentValues();

        try {
            for (ContactTokenDetails token : activeTokens) {
                Log.w("Directory", "Adding number: " + token.getNumber());
                String s = token.getNumber();
                if(!Util.isValidEmail(s) && !s.contains("@")) {
                    s = s.replace("(", "");
                    s = s.replace(")", "");
                    s = s.replace("-", "");
                    s = s.replace(" ", "");
                    s = s.length() > 10 ? s.substring(s.length() - 10) : s;
                }
                values.put(NUMBER, s);
                db.replace(TABLE_NAME, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void doDatabaseReset(Context context){
        context.deleteDatabase(DATABASE_NAME);
        instance = null;
    }

    public Set<String> getPushEligibleContactNumbers(String localNumber, String countryCode) {
        final Uri uri = Phone.CONTENT_URI;
        final Set<String> results = new HashSet<String>();
        Cursor cursor = null;
        Cursor cursorPhoneContacts = null;

        try {
            cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, new String[]{ContactsContract.CommonDataKinds.Email.DATA}, null, null, null);

            while (cursor != null && cursor.moveToNext()) {
                final String rawNumber = cursor.getString(0);
                if (rawNumber != null) {
                    if (!rawNumber.equalsIgnoreCase(localNumber)) {
                        results.add(rawNumber);
                    }
                }
            }

            if (cursor != null)
                cursor.close();

            final SQLiteDatabase readableDb = databaseHelper.getReadableDatabase();
            if (readableDb != null) {
                cursor = readableDb.query(TABLE_NAME, new String[]{NUMBER},
                        null, null, null, null, null);

                while (cursor != null && cursor.moveToNext()) {

                    if (!cursor.getString(0).equalsIgnoreCase(localNumber)) {
                        results.add(cursor.getString(0));
                    }

                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        try {
            cursorPhoneContacts = context.getContentResolver().query(uri, new String[]{Phone.NUMBER}, null, null, null);

            while (cursorPhoneContacts != null && cursorPhoneContacts.moveToNext()) {
                final String rawNumber = cursorPhoneContacts.getString(0);
                String e164Number = "";
                if (rawNumber != null) {
                    try {
                        if (countryCode == null) {
                            e164Number = PhoneNumberFormatter.formatNumber(rawNumber, localNumber);
                        } else {
                            e164Number = PhoneNumberFormatter.formatNumber(rawNumber, "+" + countryCode + Util.NUMBER_FORMAT_HELPER);
                        }
                        if (!e164Number.equalsIgnoreCase(localNumber)) {
                            results.add(e164Number);
                        }
                    } catch (InvalidNumberException e) {
                        Log.w("Directory", "Invalid number: " + rawNumber);
                    }
                }
            }
            return results;
        } finally {
            if (cursorPhoneContacts != null)
                cursorPhoneContacts.close();
        }
    }

    public List<String> getActiveNumbers() {
        final List<String> results = new ArrayList<String>();
        Cursor cursor = null;
        try {
            cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[]{NUMBER}, null, null, null, null, null);

            while (cursor != null && cursor.moveToNext()) {
                results.add(cursor.getString(0));
            }
            return results;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public Cursor   getActiveNumbersCursor(){
        Cursor cursor = null;

        cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[]{ID, NUMBER}, null, null, null, null, null);
        return cursor;
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context, String name,
                              SQLiteDatabase.CursorFactory factory,
                              int version)
        {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < INTRODUCED_CHANGE_FROM_TOKEN_TO_E164_NUMBER) {
                db.execSQL("DROP TABLE directory;");
                db.execSQL("CREATE TABLE directory ( _id INTEGER PRIMARY KEY, " +
                        "number TEXT UNIQUE);");
            }
        }
    }

}
