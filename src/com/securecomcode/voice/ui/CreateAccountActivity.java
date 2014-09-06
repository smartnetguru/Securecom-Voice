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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.securecomcode.voice.ApplicationContext;
import com.securecomcode.voice.Constants;
import com.securecomcode.voice.R;
import com.securecomcode.voice.util.PhoneNumberFormatter;
import com.securecomcode.voice.util.Util;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * The create account activity.  Kicks off an account creation event, then waits
 * the server to respond with a challenge via SMS, receives the challenge, and
 * verifies it with the server.
 *
 * @author Moxie Marlinspike
 *
 */

public class CreateAccountActivity extends SherlockActivity {

  private static final int PICK_COUNTRY = 1;

  private AsYouTypeFormatter   countryFormatter;
  private ArrayAdapter<String> countrySpinnerAdapter;
  private Spinner              countrySpinner;
  private Spinner              registrationSpinner;
  private TextView             countryCode;
  private TextView             number;
  private TextView             email;
  private Button               createButton;
  private TextView             emailLabel;
  private TextView             registrationSpinnerLabel;
  private TextView             numberLabel;
  private TextView             plusLabel;
  private TextView             countryLabel;



    @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.create_account);

    ActionBar actionBar = this.getSupportActionBar();
    actionBar.setTitle(R.string.CreateAccountActivity_register_your_redphone);

    initializeResources();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.about_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.aboutItem:
      Intent aboutIntent = new Intent(this, AboutActivity.class);
      startActivity(aboutIntent);
      return true;
    }
    return false;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PICK_COUNTRY && resultCode == RESULT_OK && data != null) {
        if(PreferenceManager.getDefaultSharedPreferences(this).getString(Constants.COUNTRY_CODE_SELECTED, "").equalsIgnoreCase("Phone")){
            this.countryCode.setText(data.getIntExtra("country_code", 1)+"");
        }
      setCountryDisplay(data.getStringExtra("country_name"));
      setCountryFormatter(data.getIntExtra("country_code", 1));
      PreferenceManager.getDefaultSharedPreferences(this).edit().putString(Constants.COUNTRY_CODE_SELECTED,  data.getIntExtra("country_code", 1)+"").commit();
    }
  }

  private void initializeResources() {
    ApplicationContext.getInstance().setContext(this);

      final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

      if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
          // You can make calls
          this.registrationSpinner = (Spinner) findViewById(R.id.registration_spinner);
          this.registrationSpinner.setVisibility(View.VISIBLE);
          this.registrationSpinnerLabel = (TextView) findViewById(R.id.registration_spinner_label);
          this.registrationSpinnerLabel.setVisibility(View.VISIBLE);
          this.registrationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
              public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                  if (parent.getItemAtPosition(pos).toString().equalsIgnoreCase("Phone Number")) {
                      countrySpinner = (Spinner) findViewById(R.id.country_spinner);
                      countrySpinner.setVisibility(View.VISIBLE);
                      countryCode = (TextView) findViewById(R.id.country_code);
                      countryCode.setVisibility(View.VISIBLE);
                      number = (TextView) findViewById(R.id.number);
                      number.setVisibility(View.VISIBLE);
                      numberLabel = (TextView) findViewById(R.id.registration_activity__your_country_code_and_phone_number_label);
                      numberLabel.setVisibility(View.VISIBLE);
                      plusLabel = (TextView) findViewById(R.id.pluslabel);
                      plusLabel.setVisibility(View.VISIBLE);
                      countryCode.addTextChangedListener(new CountryCodeChangedListener());
                      number.addTextChangedListener(new NumberChangedListener());
                      initializeSpinner();
                      initializeNumber();
                      if (email != null) {
                          email.setVisibility(View.GONE);
                      }
                      if (emailLabel != null) {
                          emailLabel.setVisibility(View.GONE);
                      }
                      preferences.edit().putString(Constants.REG_OPTION_SELECTED, "Phone").commit();
                  } else if (parent.getItemAtPosition(pos).toString().equalsIgnoreCase("Email")) {
                      email = (TextView) findViewById(R.id.email_address);
                      email.setVisibility(View.VISIBLE);
                      emailLabel = (TextView) findViewById(R.id.registration_activity__your_email_label);
                      emailLabel.setVisibility(View.VISIBLE);
                      countrySpinner = (Spinner) findViewById(R.id.country_spinner);
                      countrySpinner.setVisibility(View.VISIBLE);
                      initializeSpinner();
                      if (number != null) {
                          number.setVisibility(View.GONE);
                      }
                      if (countryCode != null) {
                          countryCode.setVisibility(View.GONE);
                      }
                      if (countryLabel != null) {
                          countryLabel.setVisibility(View.GONE);
                      }
                      if (numberLabel != null) {
                          numberLabel.setVisibility(View.GONE);
                      }
                      if (plusLabel != null) {
                          plusLabel.setVisibility(View.GONE);
                      }
                      preferences.edit().putString(Constants.REG_OPTION_SELECTED, "Email").commit();
                      preferences.edit().putString(Constants.COUNTRY_CODE_SELECTED, "0").commit();
                  }
              }

              @Override
              public void onNothingSelected(AdapterView<?> arg0) {

              }
          });
      } else {
          email = (TextView) findViewById(R.id.email_address);
          email.setVisibility(View.VISIBLE);
          emailLabel = (TextView) findViewById(R.id.registration_activity__your_email_label);
          emailLabel.setVisibility(View.VISIBLE);
          countrySpinner = (Spinner) findViewById(R.id.country_spinner);
          countrySpinner.setVisibility(View.VISIBLE);
          initializeSpinner();
          preferences.edit().putString(Constants.REG_OPTION_SELECTED, "Email").commit();
          preferences.edit().putString(Constants.COUNTRY_CODE_SELECTED, "0").commit();
      }

    this.createButton   = (Button)findViewById(R.id.registerButton);
    this.createButton.setOnClickListener(new CreateButtonListener());
  }

  private void initializeSpinner(){
      this.countrySpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
      this.countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      setCountryDisplay(getString(R.string.CreateAccountActivity_select_your_country));

      this.countrySpinner.setAdapter(this.countrySpinnerAdapter);
      this.countrySpinner.setOnTouchListener(new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
              if (event.getAction() == MotionEvent.ACTION_UP) {
                  Intent intent = new Intent(CreateAccountActivity.this, CountrySelectionActivity.class);
                  startActivityForResult(intent, PICK_COUNTRY);
              }
              return true;
          }
      });
  }


  private void initializeNumber() {
    PhoneNumberUtil numberUtil  = PhoneNumberUtil.getInstance();
    String localNumber = Util.getDeviceE164Number(this);/*((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
                         .getLine1Number();*/

    try {
      if (!Util.isEmpty(localNumber)) {
        PhoneNumber localNumberObject = numberUtil.parse(localNumber, null);

        if (localNumberObject != null) {
          this.countryCode.setText(localNumberObject.getCountryCode()+"");
          this.number.setText(localNumberObject.getNationalNumber()+"");
        }
      }else{
        String simCountryIso = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getSimCountryIso();

        if (!Util.isEmpty(simCountryIso)) {
            this.countryCode.setText(numberUtil.getCountryCodeForRegion(simCountryIso.toUpperCase()) + "");
        }
      }
    } catch (NumberParseException npe) {
      Log.w("CreateAccountActivity", npe);
    }
  }

  private void setCountryDisplay(String value) {
    this.countrySpinnerAdapter.clear();
    this.countrySpinnerAdapter.add(value);
  }

  private void setCountryFormatter(int countryCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    String regionCode    = util.getRegionCodeForCountryCode(countryCode);

    if (regionCode == null) {
      this.countryFormatter = null;
    } else {
      this.countryFormatter = util.getAsYouTypeFormatter(regionCode);
    }
  }

  private String getConfiguredE164Number() {
    return PhoneNumberFormatter.formatE164(countryCode.getText().toString(),
                                           number.getText().toString());
  }

  private class CreateButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      final CreateAccountActivity self = CreateAccountActivity.this;
      String e164number = "";

      String registerOption = PreferenceManager.getDefaultSharedPreferences(self).getString(Constants.REG_OPTION_SELECTED, "");

      if (registerOption.equalsIgnoreCase("Phone")) {
          if (Util.isEmpty(countryCode.getText())) {
              Toast.makeText(self,
                      R.string.CreateAccountActivity_you_must_specify_your_country_code,
                      Toast.LENGTH_LONG).show();
              return;
          }

          if (Util.isEmpty(number.getText())) {
              Toast.makeText(self,
                      R.string.CreateAccountActivity_you_must_specify_your_phone_number,
                      Toast.LENGTH_LONG).show();
              return;
          }

          e164number = getConfiguredE164Number();

          if (!PhoneNumberFormatter.isValidNumber(e164number)) {
              Util.showAlertDialog(self,
                      getString(R.string.CreateAccountActivity_invalid_number),
                      String.format(getString(R.string.CreateAccountActivity_the_number_you_specified_s_is_invalid), e164number));
              return;
          }
      }else if (registerOption.equalsIgnoreCase("Email")) {
          if (Util.isEmpty(email.getText())) {
              Toast.makeText(self,
                      getString(R.string.RegistrationActivity_you_must_specify_your_email),
                      Toast.LENGTH_LONG).show();
              return;
          }

          if (PreferenceManager.getDefaultSharedPreferences(self).getString(Constants.COUNTRY_CODE_SELECTED, "").equalsIgnoreCase("0")) {
              Toast.makeText(self,
                      getString(R.string.RegistrationActivity_you_must_specify_your_country),
                      Toast.LENGTH_LONG).show();
              return;
          }

          if (!Util.isValidEmail(email.getText().toString())) {
              Util.showAlertDialog(self,
                      getString(R.string.RegistrationActivity_invalid_email_address),
                      String.format(getString(R.string.RegistrationActivity_the_email_you_specified_s_is_invalid),
                              email.getText().toString())
              );
              return;
          }
      }



      AlertDialog.Builder dialog = new AlertDialog.Builder(self);
      if (registerOption.equalsIgnoreCase("Phone")) {
        dialog.setMessage(String.format(getString(R.string.CreateAccountActivity_we_will_now_verify_that_the_following_number_is_associated),
                PhoneNumberFormatter.getInternationalFormatFromE164(e164number)));
        dialog.setPositiveButton(getString(R.string.CreateAccountActivity_continue),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(self, RegistrationProgressActivity.class);
                        intent.putExtra("e164number", getConfiguredE164Number());
                        startActivity(intent);
                        finish();
                    }
                });
      } else  if (registerOption.equalsIgnoreCase("Email")) {
        dialog.setMessage(String.format(getString(R.string.RegistrationActivity_we_will_now_verify_that_the_following_email_is_associated_with_your_device_s),
                email.getText().toString()));
        dialog.setPositiveButton(getString(R.string.CreateAccountActivity_continue),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(self, RegistrationProgressActivity.class);
                        intent.putExtra("email_address", email.getText().toString());
                        startActivity(intent);
                        finish();
                    }
                }
        );
      }

      dialog.setNegativeButton(getString(R.string.CreateAccountActivity_edit), null);
      dialog.show();
    }
  }

  private class CountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (Util.isEmpty(s)) {
        setCountryDisplay(getString(R.string.CreateAccountActivity_select_your_country));
        countryFormatter = null;
        return;
      }

      int countryCode   = Integer.parseInt(s.toString());
      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
      setCountryFormatter(countryCode);
      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));

      if (!Util.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
        number.requestFocus();
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  private class NumberChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      if (countryFormatter == null)
        return;

      if (Util.isEmpty(s))
        return;

      countryFormatter.clear();

      String number          = s.toString().replaceAll("[^\\d.]", "");
      String formattedNumber = null;

      for (int i=0;i<number.length();i++) {
        formattedNumber = countryFormatter.inputDigit(number.charAt(i));
      }

      if (!s.toString().equals(formattedNumber)) {
        s.replace(0, s.length(), formattedNumber);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }
}
