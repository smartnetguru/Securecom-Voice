/**
 * Copyright (C) 2011 Whisper Systems
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
package com.securecomcode.voice.registration;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.securecomcode.voice.Constants;
import com.securecomcode.voice.R;
import com.securecomcode.voice.RedPhoneService;
import com.securecomcode.voice.contacts.ContactTokenDetails;
import com.securecomcode.voice.directory.Directory;
import com.securecomcode.voice.directory.DirectoryUpdateReceiver;
import com.securecomcode.voice.directory.NumberFilter;
import com.securecomcode.voice.gcm.GCMRegistrarHelper;
import com.securecomcode.voice.monitor.MonitorConfigUpdateReceiver;
import com.securecomcode.voice.signaling.AccountCreationException;
import com.securecomcode.voice.signaling.AccountCreationSocket;
import com.securecomcode.voice.signaling.DirectoryResponse;
import com.securecomcode.voice.signaling.RateLimitExceededException;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.ui.AccountVerificationTimeoutException;
import com.securecomcode.voice.util.DirectoryUtil;
import com.securecomcode.voice.util.PeriodicActionUtils;
import com.securecomcode.voice.util.Util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The RegisterationService handles the actual process of registration.  If it receives an
 * intent with a REGISTER_NUMBER_ACTION, it does the following through an executor:
 * <p/>
 * 1) Generate secrets.
 * 2) Register the specified number and those secrets with the server.
 * 3) Wait for a challenge SMS.
 * 4) Verify the challenge with the server.
 * 5) Start the GCM registration process.
 * 6) Retrieve the current directory.
 * <p/>
 * The RegistrationService broadcasts its state throughout this process, and also makes its
 * state available through service binding.  This enables a View to display progress.
 *
 * @author Moxie Marlinspike
 */

public class RegistrationService extends Service {

    public static final String NOTIFICATION_TITLE = "com.securecomcode.voice.NOTIFICATION_TITLE";
    public static final String NOTIFICATION_TEXT = "com.securecomcode.voice.NOTIFICATION_TEXT";
    public static final String REGISTER_NUMBER_ACTION = "com.securecomcode.voice.RegistrationService.REGISTER_NUMBER";
    public static final String VOICE_REGISTER_NUMBER_ACTION = "com.securecomcode.voice.RegistrationService.VOICE_REGISTER_NUMBER";
    public static final String VOICE_REQUESTED_ACTION = "com.securecomcode.voice.RegistrationService.VOICE_REQUESTED";
    public static final String CHALLENGE_EVENT = "com.securecomcode.voice.CHALLENGE_EVENT";
    public static final String REGISTRATION_EVENT = "com.securecomcode.voice.REGISTRATION_EVENT";
    public static final String CHALLENGE_EXTRA = "CAAChallenge";

    private static final long REGISTRATION_TIMEOUT_MILLIS = 120000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Binder binder = new RegistrationServiceBinder();

    private volatile RegistrationState registrationState = new RegistrationState(RegistrationState.STATE_IDLE);

    private volatile Handler registrationStateHandler;
    private volatile ChallengeReceiver receiver;
    private static volatile Directory directory;
    private String challenge;
    private long verificationStartTime;

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();

            if (action.equals(REGISTER_NUMBER_ACTION) ||
                    action.equals(VOICE_REGISTER_NUMBER_ACTION) ||
                    action.equals(VOICE_REQUESTED_ACTION)) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (action.equals(REGISTER_NUMBER_ACTION)) handleRegistrationIntent(intent);
                        else if (action.equals(VOICE_REGISTER_NUMBER_ACTION))
                            handleVoiceRegistrationIntent(intent);
                        else if (action.equals(VOICE_REQUESTED_ACTION))
                            handleVoiceRequestedIntent(intent);
                    }
                });

            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void shutdown() {
        shutdownChallengeListener();
        markAsVerifying(false);
        registrationState = new RegistrationState(RegistrationState.STATE_IDLE);
    }

    public synchronized int getSecondsRemaining() {
        long millisPassed;

        if (verificationStartTime == 0) millisPassed = 0;
        else millisPassed = System.currentTimeMillis() - verificationStartTime;

        return Math.max((int) (REGISTRATION_TIMEOUT_MILLIS - millisPassed) / 1000, 0);
    }

    public RegistrationState getRegistrationState() {
        return registrationState;
    }

    private synchronized void initializeChallengeListener() {
        this.challenge = null;
        receiver = new ChallengeReceiver();
        IntentFilter filter = new IntentFilter(CHALLENGE_EVENT);
        registerReceiver(receiver, filter);
    }

    private synchronized void shutdownChallengeListener() {
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    private void handleVoiceRequestedIntent(Intent intent) {
        if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
            setState(new RegistrationState(RegistrationState.STATE_VOICE_REQUESTED,
                    intent.getStringExtra("e164number"),
                    intent.getStringExtra("password")));
        }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
            setState(new RegistrationState(RegistrationState.STATE_VOICE_REQUESTED,
                    intent.getStringExtra("email_address"),
                    intent.getStringExtra("password")));
        }
    }

    private void handleVoiceRegistrationIntent(Intent intent) {
        markAsVerifying(true);

        String number = intent.getStringExtra("e164number");
        String password = intent.getStringExtra("password");
        String key = intent.getStringExtra("key");

        AccountCreationSocket socket = null;

        try {
            setState(new RegistrationState(RegistrationState.STATE_VERIFYING_VOICE, number));
            markAsVerified(number, password, key);

            socket = new AccountCreationSocket(this, number, password);

            GCMRegistrarHelper.registerClient(this, true);
            refreshDirectory(getApplicationContext(), socket, number);
            setState(new RegistrationState(RegistrationState.STATE_COMPLETE, number));
            broadcastComplete(true);
            stopService(new Intent(this, RedPhoneService.class));
        } catch (SignalingException se) {
            Log.w("RegistrationService", se);
            setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
            broadcastComplete(false);
        } finally {
            if (socket != null)
                socket.close();
        }
    }

    private void handleRegistrationIntent(Intent intent) {
        String number = "";
        markAsVerifying(true);

        if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
            number = intent.getStringExtra("e164number");
        }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
            number = intent.getStringExtra("email_address");
        }
        AccountCreationSocket socket = null;


        try {
            String password = Util.getSecret(18);
            String key = Util.getSecret(40);

            initializeChallengeListener();

            setState(new RegistrationState(RegistrationState.STATE_CONNECTING, number));
            socket = new AccountCreationSocket(this, number, password);
            socket.createAccount(false);
            socket.close();

            setState(new RegistrationState(RegistrationState.STATE_VERIFICATION, number));
            String challenge = waitForChallenge();
            socket = new AccountCreationSocket(this, number, password);
            socket.verifyAccount(challenge, key);

            markAsVerified(number, password, key);

            GCMRegistrarHelper.registerClient(this, true);
            socket = new AccountCreationSocket(this, number, password);
            refreshDirectory(getApplicationContext(), socket, number);
            socket.close();

            setState(new RegistrationState(RegistrationState.STATE_COMPLETE, number));
            broadcastComplete(true);

            stopService(new Intent(this, RedPhoneService.class));
        } catch (SignalingException se) {
            Log.w("RegistrationService", se);
            setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
            broadcastComplete(false);
        } catch (AccountVerificationTimeoutException avte) {
            Log.w("RegistrationService", avte);
            setState(new RegistrationState(RegistrationState.STATE_TIMEOUT, number));
            broadcastComplete(false);
        } catch (AccountCreationException ace) {
            Log.w("RegistrationService", ace);
            setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
            broadcastComplete(false);
        } catch (RateLimitExceededException e) {
            Log.w("RegistrationService", e);
            setState(new RegistrationState(RegistrationState.STATE_NETWORK_ERROR, number));
            broadcastComplete(false);
        } finally {
            if (socket != null)
                socket.close();

            shutdownChallengeListener();
        }
    }

    public static void refreshDirectory(final Context context, final AccountCreationSocket socket, final String localNumber) {
        if(directory == null){
            directory = Directory.getInstance(context);
        }else{
            directory.doDatabaseReset(context);
            directory = Directory.getInstance(context);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("LOCALNUMBER", localNumber).commit();

        Set<String> eligibleContactNumbers = null;

        if(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
            eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber, null);
        }else if(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
            eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber, PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.COUNTRY_CODE_SELECTED, ""));
        }

        Map<String, String> tokenMap = DirectoryUtil.getDirectoryServerTokenMap(eligibleContactNumbers);
        try {
            List<ContactTokenDetails> activeTokens = socket.retrieveDirectory(tokenMap.keySet());
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

        PeriodicActionUtils.scheduleUpdate(context, DirectoryUpdateReceiver.class);
    }

    private synchronized String waitForChallenge() throws AccountVerificationTimeoutException {
        this.verificationStartTime = System.currentTimeMillis();

        if (this.challenge == null) {
            try {
                wait(REGISTRATION_TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                throw new IllegalArgumentException(e);
            }
        }

        if (this.challenge == null)
            throw new AccountVerificationTimeoutException();

        return this.challenge;
    }

    public synchronized void challengeReceived(String challenge) {
        this.challenge = challenge;
        notifyAll();
    }

    private void retrieveDirectory(AccountCreationSocket socket) {
        try {
            DirectoryResponse response = socket.getNumberFilter();

            if (response != null) {
                NumberFilter numberFilter = new NumberFilter(response.getFilter(), response.getHashCount());
                numberFilter.serializeToFile(this);
            }
        } catch (SignalingException se) {
            Log.w("RegistrationService", se);
        }

        PeriodicActionUtils.scheduleUpdate(this, DirectoryUpdateReceiver.class);
    }

    private void markAsVerifying(boolean verifying) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = preferences.edit();

        editor.putBoolean(Constants.VERIFYING_PREFERENCE, verifying);
        editor.commit();
    }

    private void markAsVerified(String number, String password, String key) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Editor editor = preferences.edit();

        editor.putBoolean(Constants.VERIFYING_PREFERENCE, false);
        editor.putBoolean(Constants.REGISTERED_PREFERENCE, true);
        editor.putString(Constants.NUMBER_PREFERENCE, number);
        editor.putString(Constants.PASSWORD_PREFERENCE, password);
        editor.putString(Constants.KEY_PREFERENCE, key);
        editor.putLong(Constants.PASSWORD_COUNTER_PREFERENCE, 1L);
        editor.commit();
    }

    private void setState(RegistrationState state) {
        this.registrationState = state;

        if (registrationStateHandler != null) {
            registrationStateHandler.obtainMessage(state.state, state).sendToTarget();
        }
    }

    private void broadcastComplete(boolean success) {
        Intent intent = new Intent();
        intent.setAction(REGISTRATION_EVENT);

        if (success) {
            intent.putExtra(NOTIFICATION_TITLE, getString(R.string.RegistrationService_registration_complete));
            intent.putExtra(NOTIFICATION_TEXT, getString(R.string.RegistrationService_redphone_registration_has_successfully_completed));
        } else {
            intent.putExtra(NOTIFICATION_TITLE, getString(R.string.RegistrationService_registration_error));
            intent.putExtra(NOTIFICATION_TEXT, getString(R.string.RegistrationService_redphone_registration_has_encountered_a_problem));
        }

        this.sendOrderedBroadcast(intent, null);
    }

    public void setRegistrationStateHandler(Handler registrationStateHandler) {
        this.registrationStateHandler = registrationStateHandler;
    }

    public class RegistrationServiceBinder extends Binder {
        public RegistrationService getService() {
            return RegistrationService.this;
        }
    }

    private class ChallengeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.w("RegistrationService", "Got a challenge broadcast...");
            challengeReceived(intent.getStringExtra(CHALLENGE_EXTRA));
        }
    }

    public static class RegistrationState {

        public static final int STATE_IDLE = 0;
        public static final int STATE_CONNECTING = 1;
        public static final int STATE_VERIFYING_SMS = 2;
        public static final int STATE_TIMER = 3;
        public static final int STATE_COMPLETE = 4;
        public static final int STATE_TIMEOUT = 5;
        public static final int STATE_NETWORK_ERROR = 6;
        public static final int STATE_VOICE_REQUESTED = 7;
        public static final int STATE_VERIFYING_VOICE = 8;
        public static final int STATE_VERIFICATION = 9;

        public final int state;
        public final String number;
        public final String password;

        public RegistrationState(int state) {
            this(state, null);
        }

        public RegistrationState(int state, String number) {
            this(state, number, null);
        }

        public RegistrationState(int state, String number, String password) {
            this.state = state;
            this.number = number;
            this.password = password;
        }
    }
}
