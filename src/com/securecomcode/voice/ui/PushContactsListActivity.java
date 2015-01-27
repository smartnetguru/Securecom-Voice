/*
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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.securecomcode.voice.Constants;
import com.securecomcode.voice.R;
import com.securecomcode.voice.RedPhone;
import com.securecomcode.voice.RedPhoneService;
import com.securecomcode.voice.contacts.Contact;
import com.securecomcode.voice.contacts.ContactAccessor;
import com.securecomcode.voice.contacts.ContactTokenDetails;
import com.securecomcode.voice.contacts.ContactsSectionIndexer;
import com.securecomcode.voice.directory.Directory;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;
import com.securecomcode.voice.util.DirectoryUtil;
import com.securecomcode.voice.util.ProgressDialogAsyncTask;
import com.securecomcode.voice.util.Util;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.securecomcode.voice.util.Util.showAlertOnNoData;

/**
 * Activity that displays a listview of contacts for the RedPhone "dialer."
 * Both for straight contacts and "frequently contacted" tabs.
 *
 * @author Moxie Marlinspike
 *
 */

public class PushContactsListActivity extends SherlockListFragment
{

    private HashMap<Long, SoftReference<Bitmap>> photoCache
            = new HashMap<Long, SoftReference<Bitmap>>();

    private boolean showSectionHeaders = true;
    ArrayList<Contact> contactList = new ArrayList<Contact>();
    private boolean loadPushContacts = false;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case R.id.refreshItem:
                new ProgressDialogAsyncTask<Void,Void,Void>(this.getActivity(),
                        getString(R.string.ContactsListActivity_updating_contacts),
                        getString(R.string.ContactsListActivity_updating_securecom_contacts))
                {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        doDirectoryRefresh();
                        getActivity().finish();
                        startActivity(getActivity().getIntent());
                        return null;
                    }
                }.execute();
                return true;
            case R.id.pushSearchItem:
                this.getSherlockActivity().getSupportActionBar().setIcon(R.drawable.ic_tab_favorites);
                return true;
        }

        return false;
    }

    @Override
    public void onActivityCreated(Bundle icicle) {
        super.onActivityCreated(icicle);
        displayContacts();

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.contacts_list_content, container, false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.pushcontact_list_options_menu, menu);
        menu.findItem(R.id.refreshItem).setVisible(true);
        menu.findItem(R.id.pushSearchItem).setVisible(true);
        initializeSearch((android.widget.SearchView) menu.findItem(R.id.pushSearchItem).getActionView());
    }

    @SuppressLint({ "NewApi", "NewApi" })
    private void initializeSearch(android.widget.SearchView searchView) {
        searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                ArrayList<Contact> searchList = new ArrayList<Contact>();
                for (Contact c : contactList) {
                    if (query.length() <= c.getContactName().length()) {
                        if (c.getContactName().toLowerCase().contains(query.toLowerCase())) {
                            searchList.add(c);
                        }
                    }

                    for(String number: c.getPhoneNumber()){
                        if(query.length() <= number.length()){
                            String s = number;
                            if(!Util.isValidEmail(s) && !s.contains("@")) {
                                s = s.replace("(", "");
                                s = s.replace(")", "");
                                s = s.replace("-", "");
                                s = s.replace(" ", "");
                                s = s.length() > 10 ? s.substring(s.length() - 10) : s;
                            }
                            if(s.toLowerCase().contains(query.toLowerCase())){
                                if(!searchList.contains(c)) {
                                    searchList.add(c);
                                }
                            }
                        }
                    }
                }

                if(searchList.size() == 0){
                    ((TextView) getListView().getEmptyView()).setText(R.string.ContactsListActivity_no_contacts_found);
                }

                setAdapter(searchList);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return onQueryTextSubmit(newText);
            }
        });
    }

    private void doDirectoryRefresh() {
        Directory directory;
        Set<String> eligibleContactNumbers = null;
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SignalingSocket signalingSocket;

        try {
            signalingSocket = new SignalingSocket(getActivity());

            directory = Directory.getInstance(getActivity());


            if(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
                eligibleContactNumbers = directory.getPushEligibleContactNumbers(preferences.getString(("LOCALNUMBER"), ""), null);
            }else if(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
                eligibleContactNumbers = directory.getPushEligibleContactNumbers(preferences.getString(("LOCALNUMBER"), ""), PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(Constants.COUNTRY_CODE_SELECTED, ""));
            }

            Map<String, String> tokenMap = DirectoryUtil.getDirectoryServerTokenMap(eligibleContactNumbers);
            try {
                List<ContactTokenDetails> activeTokens = signalingSocket.retrieveDirectory(tokenMap.keySet());
                if (activeTokens != null) {
                    for (ContactTokenDetails activeToken : activeTokens) {
                        eligibleContactNumbers.remove(tokenMap.get(activeToken.getToken()));
                        activeToken.setNumber(tokenMap.get(activeToken.getToken()));
                    }

                    directory.setNumbers(activeTokens);
                }
            } catch (SignalingException e) {
                e.printStackTrace();
            }
            signalingSocket.close();
        } catch (SignalingException e) {
            Log.w("RedPhoneService", e);
        }catch (Exception e) {
            Log.w("DirectoryUpdateReceiver", e);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {

        if(!showAlertOnNoData(getActivity())){
            return;
        }

        ContactItemView contactItemView = (ContactItemView)v;
        final String[] numbers                = contactItemView.getContactNumbers();

        if(numbers.length > 1) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Select for " + contactItemView.getName());
            builder.setItems(numbers, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doActionOutgoingCall(numbers[which]);
                }
            });

            builder.show();
        }else{
            if(numbers.length == 1){
                doActionOutgoingCall(numbers[0]);
            }
        }

    }

    private void doActionOutgoingCall(String remotenumber){
        Intent intent = new Intent(getActivity(), RedPhoneService.class);

        intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
        intent.putExtra(Constants.REMOTE_NUMBER, remotenumber);
        getActivity().startService(intent);

        Intent activityIntent = new Intent(getActivity(), RedPhone.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(activityIntent);

        getActivity().finish();
    }

    private void displayContacts() {
        this.showSectionHeaders = !isFavoritesFragment();
        if(!loadPushContacts) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                new PushRegisteredContacts().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                new PushRegisteredContacts().execute();
            }
            loadPushContacts = true;
        }
        getListView().setDivider(null);
    }

    private void setAdapter(ArrayList<Contact> list){
        setListAdapter(new ContactsListBaseAdapter(getActivity(), list));
    }


    private class ContactsListBaseAdapter extends BaseAdapter implements SectionIndexer {
        private HashMap<String, Integer> groupingTable;
        private Context context;
        private ArrayList<Contact> list;
        private ContactsSectionIndexer indexer;

        public ContactsListBaseAdapter(Context context, ArrayList<Contact> list) {
            this.context = context;
            this.groupingTable = new HashMap<String, Integer>();
            this.list = list;
            this.indexer = new ContactsSectionIndexer(list);
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
            int section = indexer.getSectionForPosition(position);
            ContactItemView civ = new ContactItemView(context);

            if (showSectionHeaders && (position == indexer.getPositionForSection(section))) {
                civ.setSectionLabel((String)indexer.getSections()[section]);
            } else {
                civ.disableSectionLabel();
            }

            Contact c = list.get(position);
            civ.set(c.getContactName(), c.getPersonId(), c.getPhoneNumber().toArray(new String[c.getPhoneNumber().size()]));
            return civ;
        }

        @Override
        public Object[] getSections() {
            return indexer.getSections();
        }

        @Override
        public int getPositionForSection(int section) {
            return indexer.getPositionForSection(section);
        }

        @Override
        public int getSectionForPosition(int position) {
            return indexer.getSectionForPosition(position);
        }
    }

    private class ContactItemView extends RelativeLayout {
        private ImageView divider;
        private View sectionDivider;
        private TextView sectionLabel;
        private TextView name;
        private QuickContactBadge contactPhoto;
        private RelativeLayout layout;
        private String[] number;

        public ContactItemView(Context context) {
            super(context.getApplicationContext());

            LayoutInflater li = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            li.inflate(R.layout.contact_list_item, this, true);

            this.name           = (TextView)findViewById(R.id.name);
            this.sectionLabel   = (TextView)findViewById(R.id.section_label);
            this.divider        = (ImageView)findViewById(R.id.divider);
            this.sectionDivider = findViewById(R.id.section_divider);
            this.contactPhoto   = (QuickContactBadge)findViewById(R.id.contact_photo);
            this.layout = (RelativeLayout)findViewById(R.id.details_layout);
        }

        public void setSectionLabel(String label) {
            this.sectionLabel.setText(label);
            this.sectionLabel.setTypeface(sectionLabel.getTypeface(), Typeface.BOLD);
            this.sectionLabel.setVisibility(View.VISIBLE);
            this.divider.setVisibility(View.GONE);
            this.sectionDivider.setVisibility(View.VISIBLE);
        }

        public void disableSectionLabel() {
            this.sectionLabel.setVisibility(View.GONE);
            this.divider.setVisibility(View.VISIBLE);
            this.sectionDivider.setVisibility(View.GONE);
        }

        public void setContactNumbers(String[] number){
            this.number = number;
        }

        public String[] getContactNumbers(){
            return this.number;
        }

        public String getName(){
            return this.name.getText().toString();
        }

        public void set(String name, int personId, String[] number) {
            int prevTextViewId = 0;
            this.contactPhoto.setImageBitmap(loadContactPhoto(personId));
            this.name.setText(name);
            this.setContactNumbers(number);
            for(int i = 0; i < number.length; i++) {
                final TextView textView = new TextView(getContext());
                textView.setText(number[i]);
                textView.setTextColor(getResources().getColor(R.color.black));
                int curTextViewId = prevTextViewId + 1;
                textView.setId(curTextViewId);
                final LayoutParams params =
                        new LayoutParams(LayoutParams.FILL_PARENT,
                                LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.BELOW, prevTextViewId);
                textView.setLayoutParams(params);
                prevTextViewId = curTextViewId;
                layout.addView(textView, params);
            }
            this.contactPhoto.setVisibility(View.VISIBLE);
            this.name.setVisibility(View.VISIBLE);
            this.divider.setVisibility(View.VISIBLE);
        }

        private Bitmap getCachedBitmapIfAvailable(long id) {
            SoftReference<Bitmap> bitmapReference = photoCache.get(id);
            if( bitmapReference != null ) {
                Bitmap cachedBitmap = bitmapReference.get();
                if( cachedBitmap != null ) {
                    return cachedBitmap;
                } else {
                    photoCache.remove(id);
                }
            }

            return null;
        }

        private Bitmap constructNewBitmap(long id) {
            Bitmap newBitmap            = ContactAccessor.getInstance()
                    .getPhoto(PushContactsListActivity.this.getActivity(), id);
            SoftReference<Bitmap> newSR = new SoftReference<Bitmap>(newBitmap);
            photoCache.put(id,newSR);
            return newBitmap;
        }

        private Bitmap loadContactPhoto(long id) {
            Bitmap contactBitmap = getCachedBitmapIfAvailable(id);

            if (contactBitmap != null) return contactBitmap;
            else                       return constructNewBitmap(id);
        }
    }

    private boolean isFavoritesFragment() {
        return getArguments() != null && getArguments().getBoolean("favorites", false);
    }

    private class PushRegisteredContacts extends AsyncTask<Void, Void, Void> {
        private HashMap<String, Integer> groupingTable;

        @Override
        protected Void doInBackground(Void... params) {
            List<String> push = Directory.getInstance(getActivity()).getActiveNumbers();
            groupingTable = new HashMap<String, Integer>();
            Cursor cursor = getActivity().getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI,
                    null,
                    ContactsContract.Data.IN_VISIBLE_GROUP + "!=0 AND (" + ContactsContract.Data.MIMETYPE + "=? OR " + ContactsContract.Data.MIMETYPE + "=?)",
                    new String[]{ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE},
                    ContactsContract.Data.DISPLAY_NAME + " COLLATE NOCASE ASC");

            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String contactName = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                    int personId = cursor.getInt(cursor.getColumnIndex(Phone.CONTACT_ID));
                    String number = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                    int rowId = cursor.getInt(cursor.getColumnIndex(Phone._ID));

                        String numberAfterFormat = number;
                        if(!Util.isValidEmail(number) && !number.contains("@")){
                            numberAfterFormat = number.replace("(", "");
                            numberAfterFormat = numberAfterFormat.replace(")", "");
                            numberAfterFormat = numberAfterFormat.replace("-", "");
                            numberAfterFormat = numberAfterFormat.replace(" ", "");
                            numberAfterFormat = numberAfterFormat.length() > 10 ? numberAfterFormat.substring(numberAfterFormat.length() - 10) : numberAfterFormat;
                        }

                        if(push.contains(numberAfterFormat)){
                            Contact c = new Contact();

                            if (!groupingTable.containsKey(contactName) ||
                                    (groupingTable.containsKey(contactName) &&
                                            groupingTable.get(contactName) == rowId)) {
                                groupingTable.put(contactName, rowId);
                                c.setContactName(contactName);
                                c.setPersonId(personId);
                                c.setPhoneNumber(number);
                                c.setPushRegistered(true);
                                contactList.add(c);
                            } else {
                                for (Contact tempcontact : contactList) {
                                    if (tempcontact.getContactName().equals(contactName)) {

                                        if (!tempcontact.getPhoneNumber().contains(number)) {
                                            tempcontact.setPhoneNumber(number);
                                        }

                                        break;
                                    }
                                }
                            }
                        }
                }

                cursor.close();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            ((TextView) getListView().getEmptyView()).setText(R.string.ContactsListActivity_loading);
        }

        @Override
        protected void onPostExecute(Void result) {
            if (contactList.size() > 0) {
                setAdapter(contactList);
            } else {
                ((TextView) getListView().getEmptyView()).setText(R.string.ContactsListActivity_no_contacts_found);
            }
        }
    }
}
