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

package com.securecomcode.voice.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.content.CursorLoader;

import com.securecomcode.voice.directory.Directory;

import java.util.ArrayList;
import java.util.List;

public class ContactsCursorLoader extends CursorLoader {

    public static final String ID_COLUMN = ContactsContract.CommonDataKinds.Phone._ID;
    public static final String NAME_COLUMN = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
    public static final String NUMBER_TYPE_COLUMN = ContactsContract.CommonDataKinds.Phone.TYPE;
    public static final String NUMBER_COLUMN = ContactsContract.CommonDataKinds.Phone.NUMBER;
    public static final String LABEL_COLUMN = ContactsContract.CommonDataKinds.Phone.LABEL;
    public static final String CONTACT_LIST_SORT = ContactsContract.Data.DISPLAY_NAME + " COLLATE NOCASE ASC";


    private final Context  context;
    private final Cursor   androidCursor;
    private final Cursor   activePushContactsCursor;
    private final boolean  pushOnly;
    private final String[] ANDROID_PROJECTION = new String[]{ID_COLUMN,
                                                            NAME_COLUMN,
                                                            NUMBER_TYPE_COLUMN,
                                                            LABEL_COLUMN,
                                                            NUMBER_COLUMN};


    public ContactsCursorLoader(Context context, String filter, boolean pushOnly) {
        super(context);
        this.context  = context;
        this.androidCursor = queryAndroidDb(filter);
        this.pushOnly = pushOnly;
        this.activePushContactsCursor = Directory.getInstance(context).getActiveNumbersCursor();
    }

    @Override
    public Cursor loadInBackground() {

        if (pushOnly) {
            return activePushContactsCursor;
        }else{
            return androidCursor;
        }

    }

    @Override
    public void onReset() {
        super.onReset();
    }

    private Cursor queryAndroidDb(String filter) {
        Uri baseUri;
        Cursor cursor;
        List<Cursor> cursors = new ArrayList<Cursor>();

        if (filter != null) {
            baseUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                    Uri.encode(filter));

            cursor = context.getContentResolver().query(
                    baseUri,
                    null,
                    null,
                    null,
                    CONTACT_LIST_SORT);
            cursors.add(cursor);
            baseUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
                    Uri.encode(filter));
            cursor = context.getContentResolver().query(
                    baseUri,
                    null,
                    null,
                    null,
                    CONTACT_LIST_SORT);
            cursors.add(cursor);
            return new MergeCursor(cursors.toArray(new Cursor[]{}));

        } else {
            baseUri = ContactsContract.Data.CONTENT_URI;

            cursor = context.getContentResolver().query(
                    baseUri,
                    null,
                    ContactsContract.Data.IN_VISIBLE_GROUP + "!=0 AND (" + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=?)",
                    new String[]{ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE},
                    CONTACT_LIST_SORT);
            return cursor;
        }

    }

}