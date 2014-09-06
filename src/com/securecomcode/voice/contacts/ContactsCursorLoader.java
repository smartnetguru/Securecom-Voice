package com.securecomcode.voice.contacts;

import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.content.CursorLoader;
import android.util.Log;

import com.securecomcode.voice.directory.Directory;

import java.util.ArrayList;
import java.util.List;

public class ContactsCursorLoader extends CursorLoader {

    public static final String ID_COLUMN = ContactsContract.CommonDataKinds.Phone._ID;
    public static final String NAME_COLUMN = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
    public static final String NUMBER_TYPE_COLUMN = ContactsContract.CommonDataKinds.Phone.TYPE;
    public static final String NUMBER_COLUMN = ContactsContract.CommonDataKinds.Phone.NUMBER;
    public static final String LABEL_COLUMN = ContactsContract.CommonDataKinds.Phone.LABEL;
    public static final String CONTACT_LIST_SORT = NAME_COLUMN + " COLLATE NOCASE ASC";


    private final Context  context;
    private final Cursor   androidCursor;
    private final Cursor   androidPhoneCursor;
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
        this.androidPhoneCursor = queryAndroidPhoneDb(filter);
        this.pushOnly = pushOnly;
        this.activePushContactsCursor = Directory.getInstance(context).getActiveNumbersCursor();
    }

    @Override
    public Cursor loadInBackground() {

        if (pushOnly) {
            return activePushContactsCursor;
        }

        List<Cursor> cursors = new ArrayList<Cursor>();
        if (androidPhoneCursor != null) cursors.add(androidPhoneCursor);
        if (androidCursor != null) cursors.add(androidCursor);
        switch (cursors.size()) {
            case 0:
                return null;
            case 1:
                return cursors.get(0);
            default:
                return new MergeCursor(cursors.toArray(new Cursor[]{}));

        }
    }

    @Override
    public void onReset() {
        super.onReset();
    }

    private Cursor queryAndroidDb(String filter) {
        final Uri baseUri;
        if (filter != null) {
            baseUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
                    Uri.encode(filter));
        } else {
            baseUri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        }

        Cursor cursor = context.getContentResolver().query(baseUri, null, null, null, CONTACT_LIST_SORT);
        return cursor;
    }

    private Cursor queryAndroidPhoneDb(String filter) {
        final Uri baseUri;
        if (filter != null) {
            baseUri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                    Uri.encode(filter));
        } else {
            baseUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        }

        Cursor cursor = context.getContentResolver().query(baseUri, null, null, null, CONTACT_LIST_SORT);
        return cursor;
    }
}