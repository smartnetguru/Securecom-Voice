/*
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
package com.securecomcode.voice.call;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


import com.securecomcode.voice.ui.RecentCallListActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CallLogDatabase {

    private static final String DATABASE_NAME = "securecom_calllog.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "call_log";
    private static final String ID = "_id";
    public static final String NUMBER = "number";
    public static final String CONTACT_NAME = "contactName";
    public static final String DATE = "date";
    public static final String TYPE = "type";
    public static final String NUMBER_LABEL = "numberLabel";
    private RecentCallListActivity recentCallListActivity = null;

    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" + ID + " INTEGER PRIMARY KEY, " +
            NUMBER + " TEXT," +
            CONTACT_NAME + " TEXT," +
            DATE + " TEXT," +
            TYPE + " TEXT," +
            NUMBER_LABEL + " TEXT );";

    private static final Object instanceLock = new Object();
    private static volatile CallLogDatabase instance;

    public static CallLogDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new CallLogDatabase(context);
                }
            }
        }

        return instance;
    }

    private final DatabaseHelper databaseHelper;
    private final Context context;

    private CallLogDatabase(Context context) {
        this.context = context;
        this.databaseHelper = new DatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void setCallLogEntryValues(ContentValues values) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);
        if(cursor.getCount() == 200){
            deleteFirstRow(db, cursor);
        }
        db.beginTransaction();
        try {
            db.insert(TABLE_NAME, null, values);
            db.setTransactionSuccessful();
        }finally {
            db.endTransaction();
        }
        if(recentCallListActivity != null){
            recentCallListActivity.databaseContentUpdated();
        }
    }


    public void deleteFirstRow(SQLiteDatabase db, Cursor cursor){
        db.beginTransaction();
        try {
            if (cursor.moveToFirst()) {
                String rowId = cursor.getString(cursor.getColumnIndex(ID));
                db.delete(TABLE_NAME, ID + "=?", new String[]{rowId});
                db.setTransactionSuccessful();
            }
        }finally {
            db.endTransaction();
        }
    }

    public void doDatabaseReset(Context context) {
        context.deleteDatabase(DATABASE_NAME);
        instance = null;
    }

    public int getDatabaseTableRowCount(){
        int count = 0;
        try {
            SQLiteDatabase db = databaseHelper.getWritableDatabase();
            Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);
            count = cursor.getCount();
        }catch (Exception e){

        }
        return count;
    }

    public ArrayList<CallDetail> getActiveCallLog(RecentCallListActivity recentCallListActivity) {
        this.recentCallListActivity = recentCallListActivity;
        final ArrayList<CallDetail> results = new ArrayList<CallDetail>();
        Cursor cursor = null;
        try {
            cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[]{NUMBER, CONTACT_NAME, DATE, TYPE, NUMBER_LABEL}, null, null, null, null, null);

            while (cursor != null && cursor.moveToNext()) {
                results.add(cursorToCallDetail(cursor));
            }

            Collections.sort(results, new Comparator<CallDetail>() {
                @Override
                public int compare(CallDetail lhs, CallDetail rhs) {
                    long date1 = Long.parseLong(lhs.getDate());
                    long date2 = Long.parseLong(rhs.getDate());
                    return (date1>date2 ? -1 : (date1 == date2 ? 0 : 1));
                }
            });
            return results;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    private CallDetail cursorToCallDetail(Cursor cursor) {
        CallDetail callDetail = new CallDetail();
        callDetail.setNumber(cursor.getString(0));
        callDetail.setContactName(cursor.getString(1));
        callDetail.setDate(cursor.getString(2));
        callDetail.setType(cursor.getString(3));
        callDetail.setNumberLabel(cursor.getString(4));
        return callDetail;
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context, String name,
                              SQLiteDatabase.CursorFactory factory,
                              int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                db.execSQL("DROP TABLE IF EXISTS call_log;");
                onCreate(db);
            }
        }
}
