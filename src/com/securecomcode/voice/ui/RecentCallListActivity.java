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

package com.securecomcode.voice.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.securecomcode.voice.Constants;
import com.securecomcode.voice.R;
import com.securecomcode.voice.RedPhone;
import com.securecomcode.voice.RedPhoneService;
import com.securecomcode.voice.call.CallDetail;
import com.securecomcode.voice.call.CallLogDatabase;
import com.securecomcode.voice.call.CallLogDatabaseListener;

import java.util.ArrayList;

import static com.securecomcode.voice.util.Util.showAlertOnNoData;

/**
 * A tab for the dialer activity which displays recent call history.
 *
 * @author Moxie Marlinspike
 */
public class RecentCallListActivity extends SherlockListFragment implements CallLogDatabaseListener {

    @Override
    public void onActivityCreated(Bundle icicle) {
        super.onCreate(icicle);
        displayCalls();
        if (CallLogDatabase.getInstance(getActivity()).getDatabaseTableRowCount() > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                setHasOptionsMenu(true);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.recent_call_list_options_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case R.id.deleteItem:
                if (CallLogDatabase.getInstance(getActivity()).getDatabaseTableRowCount() > 0) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Delete Call log")
                            .setMessage("Are you sure you want to delete call log?")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    CallLogDatabase.getInstance(getActivity()).doDatabaseReset(getActivity());
                                    getActivity().finish();
                                    startActivity(getActivity().getIntent());
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();

                }
                return true;
        }

        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.recent_calls, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        NotificationManager notificationManager =
                (NotificationManager) getActivity().getSystemService(Activity.NOTIFICATION_SERVICE);
        notificationManager.cancel(DialerActivity.MISSED_CALL);
    }

    private void displayCalls() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            new CallLog().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }else{
            new CallLog().execute();
        }
    }

    private void setListTextAsLoading() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) getListView().getEmptyView()).setText(R.string.RecentCallListActivity_loading);
            }
        });
    }

    private void setListTextAsEmptyCall() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) getListView().getEmptyView()).setText(R.string.RecentCallListActivity_empty_call_log);
            }
        });
    }

    private void setAdapter(ArrayList<CallDetail> list) {
        setListAdapter(new CallListAdapter(getActivity(), list));
    }

    @Override
    public void databaseContentUpdated() {
        if (getActivity() != null) {
            if (this.isVisible()) {
                new CallLog().execute();
            }
        }
    }

    private class CallListAdapter extends BaseAdapter {
        private ArrayList<CallDetail> list;
        private Context context;

        CallListAdapter(Context context, ArrayList<CallDetail> list) {
            this.context = context;
            this.list = list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CallItemView view = new CallItemView(getActivity());
            CallDetail cd = list.get(position);
            ((CallItemView) view).set(cd.getContactName(), cd.getNumberLabel(), cd.getNumber(), Integer.parseInt(cd.getType()), Long.parseLong(cd.getDate()));
            return view;
        }
    }

    private class CallItemView extends RelativeLayout {
        private Context context;
        private ImageView callTypeIcon;
        private TextView date;
        private TextView label;
        private TextView number;
        private TextView line1;

        private Drawable incomingCallIcon;
        private Drawable outgoingCallIcon;
        private Drawable missedCallIcon;

        public CallItemView(Context context) {
            super(context.getApplicationContext());

            LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            li.inflate(R.layout.recent_call_item, this, true);

            this.context = context.getApplicationContext();
            this.callTypeIcon = (ImageView) findViewById(R.id.call_type_icon);
            this.date = (TextView) findViewById(R.id.date);
            this.label = (TextView) findViewById(R.id.label);
            this.number = (TextView) findViewById(R.id.number);
            this.line1 = (TextView) findViewById(R.id.line1);
            this.incomingCallIcon = getResources().getDrawable(R.drawable.ic_call_log_list_incoming_call);
            this.outgoingCallIcon = getResources().getDrawable(R.drawable.ic_call_log_list_outgoing_call);
            this.missedCallIcon = getResources().getDrawable(R.drawable.ic_call_log_list_missed_call);
        }

        public void set(String name, String label, String number, int type, long date) {
            this.line1.setText((name == null || name.equals("")) ? number : name);
            this.number.setText((name == null || name.equals("")) ? "" : number);
            this.label.setText(label);
            this.date.setText(DateUtils.getRelativeDateTimeString(context, date,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE));

            if (type == Calls.INCOMING_TYPE) {
                callTypeIcon.setImageDrawable(incomingCallIcon);
            } else if (type == Calls.OUTGOING_TYPE) {
                callTypeIcon.setImageDrawable(outgoingCallIcon);
            } else if (type == Calls.MISSED_TYPE) {
                callTypeIcon.setImageDrawable(missedCallIcon);
            }
        }

        public String getNumber() {
            if (this.number.getText().toString().equals(""))
                return this.line1.getText().toString();

            return this.number.getText().toString();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (!showAlertOnNoData(getActivity())) {
            return;
        }
        Intent intent = new Intent(getActivity(), RedPhoneService.class);
        intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
        intent.putExtra(Constants.REMOTE_NUMBER, ((CallItemView) v).getNumber());
        getActivity().startService(intent);

        Intent activityIntent = new Intent(getActivity(), RedPhone.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(activityIntent);

        getActivity().finish();
    }

    private class CallLog extends AsyncTask<Void, Void, Void> {
        private ArrayList<CallDetail> callLogList;

        @Override
        protected Void doInBackground(Void... params) {
            callLogList = CallLogDatabase.getInstance(getActivity()).getActiveCallLog(RecentCallListActivity.this);
            return null;
        }

        @Override
        protected void onPreExecute() {
            setListTextAsLoading();
        }

        @Override
        protected void onPostExecute(Void result) {

            if (callLogList.size() > 0) {
                setAdapter(callLogList);
            } else {
                setListTextAsEmptyCall();
            }
        }

    }

}
