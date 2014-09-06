/*
 * Copyright (C) 2013 Open Whisper Systems
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
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.thoughtcrimegson.Gson;
import com.securecomcode.voice.R;
import com.securecomcode.voice.monitor.CallQualityConfig;
import com.securecomcode.voice.monitor.UploadService;
import com.securecomcode.voice.monitor.UserFeedback;
import com.securecomcode.voice.monitor.stream.EncryptedOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static com.securecomcode.voice.monitor.stream.EncryptedStreamUtils.getPublicKeyFromResource;

/**
 * Displays dialog boxes for the call quality reporting system, and send call quality data to the
 * call metrics server.  The initial dialog alerts the user that we've added call quality reporting
 * and asks them to opt in.  The standard dialog displays an overall star rating for the call and
 * a list of potential call issues that are stored in the application preferences.
 *
 * @author Jazz Alyxzander
 * @author Stuart O. Anderson
 */

public class CallQualityDialog extends Activity {
  private static final String METRIC_DATA_TYPE = "user-feedback";
  private static final int NUM_QUESTIONS_TO_DISPLAY = 3;
  private static final float DEFAULT_RATING = 1.5f;
  public static final int CALL_QUALITY_NOTIFICATION_ID = 1982;

  private long callId;
  private Button sendButton;
  private Button doneDialogButton;
  private List<String> feedbackQuestions;

  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    callId = getIntent().getLongExtra("callId", -1);
    feedbackQuestions = getFeedbackQuestions();
    setupInterface();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    callId = getIntent().getLongExtra("callId", callId);
  }

  private List<String> getFeedbackQuestions() {
    CallQualityConfig config = ApplicationPreferencesActivity.getCallQualityConfig(this);
    List<String> questions = config.getCallQualityQuestions();
    if(questions.size() > NUM_QUESTIONS_TO_DISPLAY){
      questions = questions.subList(0, NUM_QUESTIONS_TO_DISPLAY);
    }
    return questions;
  }

  private void setViewToInitialDialog() {
    setContentView(R.layout.call_quality_initial_dialog);
    setTitle(R.string.CallQualityDialog__we_re_making_changes);
  }

  private void setViewToStandardDialog() {
    setContentView(R.layout.call_quality_dialog);
    setTitle(R.string.CallQualityDialog_call_feedback);
    TextView listLabel = (TextView)findViewById(R.id.issueLabel);
    LinearLayout list = (LinearLayout)findViewById(android.R.id.list);

    if(feedbackQuestions.isEmpty()){
      listLabel.setVisibility(View.GONE);
    } else {
      addQuestions(list);
    }
  }

  private void addQuestions(LinearLayout list) {
    for(String question : feedbackQuestions) {
      CheckBox checkBox = new CheckBox(this);
      checkBox.setText(question);
      list.addView(checkBox);
    }
  }

  private void initializeInitialDialogResources() {
    this.doneDialogButton        		= (Button)findViewById(R.id.doneDialogButton);
    this.doneDialogButton.setOnClickListener(new DoneDialogListener());

    TextView descText = (TextView) findViewById(R.id.description);

    String pretext =    getString(R.string.CallQualityDialog__whispersystems_needs_your_help_pretext);
    String posttext =   getString(R.string.CallQualityDialog__whispersystems_needs_your_help_posttext);
    String link =       getString(R.string.CallQualityDialog__linktext);

    SpannableString spanString = new SpannableString(pretext + link + posttext);

    spanString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        Intent intent = new Intent(CallQualityDialog.this, CallMetricsInfoActivity.class);
        startActivity(intent);
      }
    }, pretext.length(), pretext.length() + link.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    descText.setText(spanString);
    descText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private void initializeStandardDialogResources() {
    RatingBar ratingBar = (RatingBar)findViewById(R.id.callRatingBar);
    ratingBar.setRating(DEFAULT_RATING);
    this.sendButton        	= (Button)findViewById(R.id.sendButton);
    this.sendButton.setOnClickListener(new SendButtonListener());
  }

  private void setupInterface() {
    if(!ApplicationPreferencesActivity.wasUserNotifedOfCallQaulitySettings(this)) {
      setViewToInitialDialog();
      initializeInitialDialogResources();
    } else {
      setViewToStandardDialog();
      initializeStandardDialogResources();
    }
  }

  private UserFeedback buildUserFeedbackObject() {
    UserFeedback userFeedback = new UserFeedback();
    userFeedback.setRating(((RatingBar)findViewById(R.id.callRatingBar)).getRating());

    LinearLayout list = (LinearLayout)findViewById(android.R.id.list);

    for (int i = 0; i < feedbackQuestions.size(); i++) {
      userFeedback.addQuestionResponse(feedbackQuestions.get(i),((CheckBox)list.getChildAt(i)).isChecked());
    }
    return userFeedback;
  }

  private void sendData() {
    Writer writer = null;
    try {
      File cacheSubdir = new File(this.getCacheDir(), "/calldata");
      cacheSubdir.mkdir();
      PublicKey publicKey = getPublicKeyFromResource(getResources(), R.raw.call_metrics_public);

      File jsonFile = File.createTempFile("userfeedback", ".json", cacheSubdir);

      Log.d("CallDataImpl", "Writing output to " + jsonFile.getAbsolutePath());

      BufferedOutputStream bufferedStream = new BufferedOutputStream(new FileOutputStream(jsonFile));
      OutputStream outputStream = new EncryptedOutputStream(bufferedStream, publicKey);
      GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
      writer = new OutputStreamWriter(gzipStream);

      UserFeedback feedbackObject = buildUserFeedbackObject();
      writer.write(new Gson().toJson(feedbackObject));
      writer.close();

      UploadService.beginUpload(this, String.valueOf(callId), METRIC_DATA_TYPE, jsonFile);
    } catch (IOException e){
      Log.e("CallQualityDialog", "failed to write quality data to cache", e);
    } catch (InvalidKeySpecException e) {
      Log.e("CallQualityDialog", "failed setup stream encryption", e);
    } finally {
      if(null != writer) {
        try{ writer.close(); } catch (Exception e){}
      }
    }
    finish();
  }

  private class DoneDialogListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      boolean optIn = ((CheckBox)findViewById(R.id.optInCheckBox)).isChecked();
      boolean enableDialog = ((CheckBox)findViewById(R.id.enableDialogCheckBox)).isChecked();

      ApplicationPreferencesActivity.setMetricsOptInFlag(getApplicationContext(), optIn);
      ApplicationPreferencesActivity.setDisplayDialogPreference(getApplicationContext(), enableDialog);
      ApplicationPreferencesActivity.setUserNotfiedOfCallQualitySettings(getApplicationContext(),true);
      if(enableDialog) {
        setupInterface();
      } else {
        cancelNotification();
        finish();
      }
    }
  }

  private class SendButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... voids) {
          sendData();
          return null;
        }
      }.execute();
      cancelNotification();
    }
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
    cancelNotification();
  }

  private void cancelNotification() {
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    nm.cancel(CALL_QUALITY_NOTIFICATION_ID);
  }
}
