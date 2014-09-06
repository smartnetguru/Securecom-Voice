/*
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
package com.securecomcode.voice.ui;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.securecomcode.voice.Constants;
import com.securecomcode.voice.R;
import com.securecomcode.voice.registration.RegistrationService;
import com.securecomcode.voice.registration.RegistrationService.RegistrationState;
import com.securecomcode.voice.signaling.AccountCreationException;
import com.securecomcode.voice.signaling.AccountCreationSocket;
import com.securecomcode.voice.signaling.RateLimitExceededException;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.util.PhoneNumberFormatter;
import com.securecomcode.voice.util.Util;

public class RegistrationProgressActivity extends SherlockActivity {

  private static final int FOCUSED_COLOR   = Color.parseColor("#ff333333");
  private static final int UNFOCUSED_COLOR = Color.parseColor("#ff808080");

  private ServiceConnection    serviceConnection        = new RegistrationServiceConnection();
  private Handler              registrationStateHandler = new RegistrationStateHandler();
  private RegistrationReceiver registrationReceiver     = new RegistrationReceiver();

  private RegistrationService registrationService;

  private LinearLayout registrationLayout;
  private LinearLayout verificationFailureLayout;
  private LinearLayout connectivityFailureLayout;
  private LinearLayout smsVerificationLayout;

  private ProgressBar registrationProgress;
  private ProgressBar connectingProgress;
  private ProgressBar verificationProgress;
  private ImageView   connectingCheck;
  private ImageView   verificationCheck;
  private TextView    connectingText;
  private TextView    verificationText;
  private TextView    registrationTimerText;
  private TextView    verificationFailureLabel;
  private TextView    verificationFailureWaitingLabel;
  private TextView    voiceVerificationLabel;
  private TextView    telephonyTextLabel;
  private TextView    smsTextLabel;
  private TextView    smsTextDesLabel;
  private EditText    codeEditText;
  private EditText    codeSmsEditText;
  private Button      editButton;
  private Button      verificationFailureButton;
  private Button      connectivityFailureButton;
  private Button      callButton;
  private Button      verifyButton;
  private Button      verifySmsButton;
  private Button      smsVerificationFailureButton;
  private ImageView   voiceImage;

  private volatile boolean visible;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
        this.getSupportActionBar().setTitle(R.string.RegistrationProgressActivity_verifying_number);
    }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
        this.getSupportActionBar().setTitle(R.string.RegistrationProgressActivity_verifying_number);
    }

    setContentView(R.layout.registration_progress);

    initializeResources();
    initializeLinks();
    initializeServiceBinding();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdownServiceBinding();
  }

  @Override
  public void onResume() {
    super.onResume();
    handleActivityVisible();
  }

  @Override
  public void onPause() {
    super.onPause();
    handleActivityNotVisible();
  }

  @Override
  public void onBackPressed() {

  }

  private void initializeServiceBinding() {
    Intent intent = new Intent(this, RegistrationService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void initializeResources() {
    this.registrationLayout        = (LinearLayout)findViewById(R.id.registering_layout);
    this.verificationFailureLayout = (LinearLayout)findViewById(R.id.verification_failure_layout);
    this.connectivityFailureLayout = (LinearLayout)findViewById(R.id.connectivity_failure_layout);
    this.smsVerificationLayout     = (LinearLayout)findViewById(R.id.sms_verification_layout);
    this.registrationProgress      = (ProgressBar) findViewById(R.id.registration_progress);
    this.connectingProgress        = (ProgressBar) findViewById(R.id.connecting_progress);
    this.verificationProgress      = (ProgressBar) findViewById(R.id.verification_progress);
    this.connectingCheck           = (ImageView)   findViewById(R.id.connecting_complete);
    this.verificationCheck         = (ImageView)   findViewById(R.id.verification_complete);
    this.connectingText            = (TextView)    findViewById(R.id.connecting_text);
    this.verificationText          = (TextView)    findViewById(R.id.verification_text);
    this.registrationTimerText     = (TextView)    findViewById(R.id.registration_timer);
    this.verificationFailureLabel  = (TextView)    findViewById(R.id.registration_progress_activity__sms_verification_failed_label);
    this.verificationFailureWaitingLabel  = (TextView)    findViewById(R.id.sms_failed_text);
    this.voiceVerificationLabel    = (TextView)    findViewById(R.id.registration_progress_activity__voice_verification_label);
    this.telephonyTextLabel        = (TextView)    findViewById(R.id.telephone_text);
    this.smsTextLabel              = (TextView)    findViewById(R.id.sms_text_label);
    this.smsTextDesLabel           = (TextView)    findViewById(R.id.sms_text);
    this.codeEditText              = (EditText)    findViewById(R.id.telephone_code);
    this.codeSmsEditText           = (EditText)    findViewById(R.id.sms_code);
    this.editButton                = (Button)      findViewById(R.id.edit_button);
    this.verificationFailureButton = (Button)      findViewById(R.id.verification_failure_edit_button);
    this.connectivityFailureButton = (Button)      findViewById(R.id.connectivity_failure_edit_button);
    this.callButton                = (Button)      findViewById(R.id.call_button);
    this.verifyButton              = (Button)      findViewById(R.id.verify_button);
    this.verifySmsButton           = (Button)      findViewById(R.id.sms_verify_button);
    this.smsVerificationFailureButton = (Button)      findViewById(R.id.sms_verification_failure_edit_button);
    this.voiceImage                = (ImageView)   findViewById(R.id.telephone);

    if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
        this.verificationText.setText(R.string.registration_progress__waiting_for_sms_verification);
    }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
        this.verificationText.setText(R.string.registration_progress_activity__waiting_for_email_verification);
    }

    this.editButton.setOnClickListener(new EditButtonListener());
    this.verificationFailureButton.setOnClickListener(new EditButtonListener());
    this.smsVerificationFailureButton.setOnClickListener(new EditButtonListener());
    this.connectivityFailureButton.setOnClickListener(new EditButtonListener());
  }

  private void initializeLinks() {
    TextView        failureText     = (TextView) findViewById(R.id.sms_failed_text);
    String          pretext         = getString(R.string.registration_progress__redphone_timed_out_while_waiting_for_an_sms_message_to_verify_your_phone_number);
    String          link            = getString(R.string.RegistrationProgressActivity_possible_problems);
    SpannableString spannableString = new SpannableString(pretext + " " + link);

    spannableString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        Intent intent = new Intent(RegistrationProgressActivity.this,
                                   RegistrationProblemsActivity.class);
        startActivity(intent);
      }
    }, pretext.length() + 1, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    failureText.setText(spannableString);
    failureText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private void handleActivityVisible() {
    IntentFilter filter = new IntentFilter(RegistrationService.REGISTRATION_EVENT);
    filter.setPriority(1000);
    registerReceiver(registrationReceiver, filter);
    visible = true;
  }

  private void handleActivityNotVisible() {
    unregisterReceiver(registrationReceiver);
    visible = false;
  }

  private void handleStateIdle() {
    if (hasNumberDirective()) {
      Intent intent = new Intent(this, RegistrationService.class);
      intent.setAction(RegistrationService.REGISTER_NUMBER_ACTION);
      if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
          intent.putExtra("e164number", getNumberDirective());
      }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
          intent.putExtra("email_address", getNumberDirective());
      }
      startService(intent);
    } else {
      startActivity(new Intent(this, CreateAccountActivity.class));
      finish();
    }
  }

  private void handleStateConnecting() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.VISIBLE);
    this.connectingCheck.setVisibility(View.INVISIBLE);
    this.verificationProgress.setVisibility(View.INVISIBLE);
    this.verificationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(FOCUSED_COLOR);
    this.verificationText.setTextColor(UNFOCUSED_COLOR);
    this.smsVerificationLayout.setVisibility(View.GONE);
  }

  private void handleStateVerifyingSms() {
    this.verificationText.setText(getString(R.string.registration_progress__waiting_for_sms_verification));
    handleStateVerifying();
  }

  private void handleStateVerifyingVoice() {
    this.verificationText.setText(getString(R.string.RegistrationProgressActivity_verifying_voice_code));
    handleStateVerifying();
  }

  private void handleStateVerifying() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.verificationProgress.setVisibility(View.VISIBLE);
    this.verificationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.verificationText.setTextColor(FOCUSED_COLOR);
  }

  private void handleVerificationRequestedVoice(RegistrationState state) {
    handleVerificationTimeout(state);
    verifyButton.setOnClickListener(new VerifyClickListener(state.number, state.password));
    verifyButton.setEnabled(true);
    codeEditText.setEnabled(true);
  }

  private void handleVerificationTimeout(RegistrationState state) {
    this.callButton.setOnClickListener(new CallClickListener(state.number));
    this.verifyButton.setEnabled(false);
    this.codeEditText.setEnabled(false);
    this.registrationLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.smsVerificationLayout.setVisibility(View.GONE);
    this.verificationFailureLayout.setVisibility(View.VISIBLE);
    /*this.verificationFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
                                                         PhoneNumberFormatter.formatNumberInternational(state.number)));*/
    if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
        handleSmsVerificationTimeout(state);
    }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
        handleEmailVerificationTimeout(state);
    }
  }

  private void handleSmsVerificationTimeout(RegistrationState state){
      verificationFailureLabel.setText(R.string.registration_progress__sms_verification_failed);
      verificationFailureWaitingLabel.setText(R.string.registration_progress_activity__timed_out_while_waiting_for_a_verification_sms_message);
      this.verificationFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
              PhoneNumberFormatter.formatNumberInternational(state.number)));
  }

  private void handleEmailVerificationTimeout(RegistrationState state){
      verificationFailureLabel.setText(R.string.registration_progress_activity__email_verification_failed);
      verificationFailureWaitingLabel.setText(R.string.registration_progress_activity__timed_out_while_waiting_for_a_verification_email_message);
      voiceImage.setVisibility(View.GONE);
      voiceVerificationLabel.setVisibility(View.GONE);
      telephonyTextLabel.setVisibility(View.GONE);
      this.verifyButton.setVisibility(View.GONE);
      this.callButton.setVisibility(View.GONE);
      codeEditText.setVisibility(View.GONE);
      this.verificationFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
              state.number));
  }

    private void handleVerification(RegistrationState state) {

        if (!Util.isAndroidPhone()) {
            if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")) {
                this.smsTextLabel.setText(R.string.registration_progress_activity__sms_verification);
                this.smsTextDesLabel.setText(R.string.registration_progress_activity__sms_you_to_verify_your_number);
            }
        }
        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")) {
            this.smsTextLabel.setText(R.string.registration_progress_activity__email_verification);
            this.smsTextDesLabel.setText(R.string.registration_progress_activity__email_you_to_verify_your_number);
        }

        if (!Util.isAndroidPhone() || PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")) {
            this.verifySmsButton.setEnabled(true);
            this.verifySmsButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String code = RegistrationProgressActivity.this.codeSmsEditText.getText().toString();
                    if (Util.isEmpty(code)) {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.RegistrationActivity_you_must_specify_your_verification_code),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    RegistrationProgressActivity.this.registrationService.challengeReceived(code);
                }
            });
            this.codeSmsEditText.setEnabled(true);
            this.registrationLayout.setVisibility(View.GONE);
            this.smsVerificationLayout.setVisibility(View.VISIBLE);
        } else {
            this.registrationLayout.setVisibility(View.VISIBLE);
        }
        if (!Util.isValidEmail(state.number)) {
            this.smsVerificationFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
                    PhoneNumberFormatter.formatNumberInternational(state.number)));
        } else {
            this.smsVerificationFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
                    state.number));
        }

    }

  private void handleConnectivityError(RegistrationState state) {
    this.registrationLayout.setVisibility(View.GONE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.smsVerificationLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.VISIBLE);
    this.connectivityFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
                                                         PhoneNumberFormatter.formatNumberInternational(state.number)));
  }

  private void handleVerificationComplete() {
    if (visible) {
      Toast.makeText(this, R.string.RegistrationProgressActivity_registration_complete, Toast.LENGTH_LONG).show();
    }

    shutdownService();
    startActivity(new Intent(this, DialerActivity.class));
    finish();
  }

  private void handleTimerUpdate() {
    if (registrationService == null)
      return;

    int totalSecondsRemaining = registrationService.getSecondsRemaining();
    int minutesRemaining      = totalSecondsRemaining / 60;
    int secondsRemaining      = totalSecondsRemaining - (minutesRemaining * 60);
    double percentageComplete = (double)((60 * 2) - totalSecondsRemaining) / (double)(60 * 2);
    int progress              = (int)Math.round(((double)registrationProgress.getMax()) * percentageComplete);

    this.registrationProgress.setProgress(progress);
    this.registrationTimerText.setText(String.format("%02d:%02d", minutesRemaining, secondsRemaining));

    registrationStateHandler.sendEmptyMessageDelayed(RegistrationState.STATE_TIMER, 1000);
  }

  private boolean hasNumberDirective() {
    boolean result = false;
    if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
        result = getIntent().getStringExtra("e164number") != null;
    }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
        result = getIntent().getStringExtra("email_address") != null;
    }
    return result;
  }

  private String getNumberDirective() {
      String result = "";
      if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
          result = getIntent().getStringExtra("e164number");
      }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
          result = getIntent().getStringExtra("email_address");
      }
      return result;
  }

  private void shutdownServiceBinding() {
    if (serviceConnection != null) {
      unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  private void shutdownService() {
    if (registrationService != null) {
      registrationService.shutdown();
      registrationService = null;
    }

    shutdownServiceBinding();

    Intent serviceIntent = new Intent(RegistrationProgressActivity.this, RegistrationService.class);
    stopService(serviceIntent);
  }

  private class RegistrationServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      registrationService  = ((RegistrationService.RegistrationServiceBinder)service).getService();
      registrationService.setRegistrationStateHandler(registrationStateHandler);

      RegistrationState state = registrationService.getRegistrationState();
      registrationStateHandler.obtainMessage(state.state, state).sendToTarget();

      handleTimerUpdate();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      registrationService.setRegistrationStateHandler(null);
    }
  }

  private class RegistrationStateHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      RegistrationState state = (RegistrationState)message.obj;

      switch (message.what) {
      case RegistrationState.STATE_IDLE:            handleStateIdle();                       break;
      case RegistrationState.STATE_CONNECTING:      handleStateConnecting();                 break;
      case RegistrationState.STATE_VERIFYING_SMS:   handleStateVerifyingSms();               break;
      case RegistrationState.STATE_VERIFYING_VOICE: handleStateVerifyingVoice();             break;
      case RegistrationState.STATE_TIMER:           handleTimerUpdate();                     break;
      case RegistrationState.STATE_TIMEOUT:         handleVerificationTimeout(state);        break;
      case RegistrationState.STATE_VERIFICATION:    handleVerification(state);               break;
      case RegistrationState.STATE_COMPLETE:        handleVerificationComplete();            break;
      case RegistrationState.STATE_NETWORK_ERROR:   handleConnectivityError(state);          break;
      case RegistrationState.STATE_VOICE_REQUESTED: handleVerificationRequestedVoice(state); break;
      }
    }
  }

  private class EditButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      shutdownService();

      Intent activityIntent = new Intent(RegistrationProgressActivity.this, CreateAccountActivity.class);
      startActivity(activityIntent);
      finish();
    }
  }

  private class RegistrationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      abortBroadcast();
    }
  }

  private class VerifyClickListener implements View.OnClickListener {
    private static final int SUCCESS            = 0;
    private static final int NETWORK_ERROR      = 1;
    private static final int RATE_LIMIT_ERROR   = 2;
    private static final int VERIFICATION_ERROR = 3;

    private final String e164number;
    private final String password;
    private final String key;
    private final Context context;

    private ProgressDialog progressDialog;

    public VerifyClickListener(String e164number, String password) {
      this.e164number = e164number;
      this.password   = password;
      this.context    = RegistrationProgressActivity.this;
      this.key        = Util.getSecret(40);
    }

    @Override
    public void onClick(View v) {
      final String code = codeEditText.getText().toString();

      if (Util.isEmpty(code)) {
        Toast.makeText(context,
                       getString(R.string.RegistrationProgressActivity_you_must_enter_the_code_you_received_first),
                       Toast.LENGTH_LONG).show();
        return;
      }

      new AsyncTask<Void, Void, Integer>() {

        @Override
        protected void onPreExecute() {
          progressDialog = ProgressDialog.show(context,
                                               getString(R.string.RegistrationProgressActivity_connecting),
                                               getString(R.string.RegistrationProgressActivity_connecting_for_verification),
                                               true, false);
        }

        @Override
        protected void onPostExecute(Integer result) {
          if (progressDialog != null) progressDialog.dismiss();

          switch (result) {
            case SUCCESS:
              Intent intent = new Intent(context, RegistrationService.class);
              intent.setAction(RegistrationService.VOICE_REGISTER_NUMBER_ACTION);
              if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
                  intent.putExtra("e164number", e164number);
              }else if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
                  intent.putExtra("email_address", e164number);
              }
              intent.putExtra("password", password);
              intent.putExtra("key", key);
              startService(intent);
              break;
            case NETWORK_ERROR:
              Util.showAlertDialog(context, getString(R.string.RegistrationProgressActivity_network_error),
                                   getString(R.string.RegistrationProgressActivity_unable_to_connect));
              break;
            case VERIFICATION_ERROR:
              Util.showAlertDialog(context, getString(R.string.RegistrationProgressActivity_verification_failed),
                                   getString(R.string.RegistrationProgressActivity_the_verification_code_you_submitted_is_incorrect));
              break;
            case RATE_LIMIT_ERROR:
              Util.showAlertDialog(context, getString(R.string.RegistrationProgressActivity_too_many_attempts),
                                   getString(R.string.RegistrationProgressActivity_youve_submitted_an_incorrect_verification_code_too_many_times));
              break;
          }
        }

        @Override
        protected Integer doInBackground(Void... params) {
          AccountCreationSocket socket = null;

          try {
            socket = new AccountCreationSocket(context, e164number, password);
            socket.verifyAccount(code, key);
            return SUCCESS;
          } catch (SignalingException e) {
            Log.w("RegistrationProgressActivity", e);
            return NETWORK_ERROR;
          } catch (AccountCreationException e) {
            Log.w("RegistrationProgressActivity", e);
            return VERIFICATION_ERROR;
          } catch (RateLimitExceededException e) {
            Log.w("RegistrationProgressActivity", e);
            return RATE_LIMIT_ERROR;
          } finally {
            if (socket != null)
              socket.close();
          }
        }
      }.execute();
    }
  }


  private class CallClickListener implements View.OnClickListener {

    private static final int SUCCESS             = 0;
    private static final int NETWORK_ERROR       = 1;
    private static final int RATE_LIMIT_EXCEEDED = 2;
    private static final int CREATE_ERROR        = 3;

    private final String  e164number;
    private final String password;
    private final Context context;

    public CallClickListener(String e164number) {
      this.e164number = e164number;
      this.password   = Util.getSecret(18);
      this.context    = RegistrationProgressActivity.this;
    }

    @Override
    public void onClick(View v) {
      new AsyncTask<Void, Void, Integer>() {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
          progressDialog = ProgressDialog.show(context, getString(R.string.RegistrationProgressActivity_requesting_call),
                                               getString(R.string.RegistrationProgressActivity_requesting_incoming_call),
                                               true, false);
        }

        @Override
        protected void onPostExecute(Integer result) {
          if (progressDialog != null) progressDialog.dismiss();

          switch (result) {
            case SUCCESS:
              Intent intent = new Intent(context, RegistrationService.class);
              intent.setAction(RegistrationService.VOICE_REQUESTED_ACTION);
              if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")) {
                  intent.putExtra("e164number", e164number);
              }
              intent.putExtra("password", password);
              startService(intent);

              callButton.setEnabled(false);
              new Handler().postDelayed(new Runnable(){
                @Override
                public void run() {
                  callButton.setEnabled(true);
                }
              }, 15000);
              break;
            case NETWORK_ERROR:
              Util.showAlertDialog(context,
                                   getString(R.string.RegistrationProgressActivity_network_error),
                                   getString(R.string.RegistrationProgressActivity_unable_to_connect));
              break;
            case CREATE_ERROR:
              Util.showAlertDialog(context,
                                   getString(R.string.RegistrationProgressActivity_server_error),
                                   getString(R.string.RegistrationProgressActivity_the_server_encountered_an_error));
              break;
            case RATE_LIMIT_EXCEEDED:
              Util.showAlertDialog(context,
                                   getString(R.string.RegistrationProgressActivity_too_many_requests),
                                   getString(R.string.RegistrationProgressActivity_youve_already_requested_a_voice_call));
              break;
          }
        }

        @Override
        protected Integer doInBackground(Void... params) {
          AccountCreationSocket socket = null;

          try {
            socket = new AccountCreationSocket(context, e164number, password);
            socket.createAccount(true);
            return SUCCESS;
          } catch (SignalingException se) {
            Log.w("RegistrationProgressActivity", se);
            return NETWORK_ERROR;
          } catch (AccountCreationException e) {
            Log.w("RegistrationProgressActivity", e);
            return CREATE_ERROR;
          } catch (RateLimitExceededException e) {
            Log.w("RegistrationProgressActivity", e);
            return RATE_LIMIT_EXCEEDED;
          } finally {
            if (socket != null)
              socket.close();
          }
        }
      }.execute();
    }
  }
}
