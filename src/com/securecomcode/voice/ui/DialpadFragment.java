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

import java.util.HashSet;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TableRow;
import android.widget.TextView;
import android.content.ContentResolver;
import android.provider.Settings;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;

import com.securecomcode.voice.ui.DialpadKeyButton;

import com.securecomcode.voice.Constants;
import com.securecomcode.voice.R;
import com.securecomcode.voice.RedPhone;
import com.securecomcode.voice.RedPhoneService;
import com.securecomcode.voice.util.Util;

import static com.securecomcode.voice.util.Util.showAlertOnNoData;

public class DialpadFragment extends SherlockFragment implements
		View.OnClickListener, View.OnLongClickListener, View.OnKeyListener,
		DialpadKeyButton.OnPressedListener, TextWatcher

{

	private View mDigitsContainer;
	private EditText mDigits;
	private View mDelete;
	private View mDialpad;
	private View mSpacer;
	private boolean mWasEmptyBeforeTextChange;
	private boolean mDigitsFilledByIntent;
	private OnDialpadQueryChangedListener mDialpadQueryListener;
	private static final String TAG = DialpadFragment.class.getSimpleName();
	private static final int TONE_LENGTH_INFINITE = -1;
	private static final int TONE_LENGTH_MS = 150;
	private boolean mDTMFToneEnabled;
	private ToneGenerator mToneGenerator;
	private final Object mToneGeneratorLock = new Object();
	private View mDialButton;
	/**
	 * Stream type used to play the DTMF tones off call, and mapped to the
	 * volume control keys
	 */
	private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;
	/** The DTMF tone volume relative to other sounds in the stream */
	private static final int TONE_RELATIVE_VOLUME = 80;
	/**
	 * Set of dialpad keys that are currently being pressed
	 */
	private final HashSet<View> mPressedDialpadKeys = new HashSet<View>(12);
	/**
	 * Remembers if we need to clear digits field when the screen is completely
	 * gone.
	 */
	private boolean mClearDigitsOnStop;

	@Override
	public void onActivityCreated(Bundle icicle) {
		super.onCreate(icicle);
	}

	public interface OnDialpadQueryChangedListener {
		void onDialpadQueryChanged(String query);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		final View fragmentView = inflater.inflate(R.layout.dialpad_fragment,
				container, false);

		mDigitsContainer = fragmentView.findViewById(R.id.digits_container);
		mDigits = (EditText) fragmentView.findViewById(R.id.digits);
        //Hide the soft
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		/* mDigits.setKeyListener(UnicodeDialerKeyListener.INSTANCE); */
		mDigits.setOnClickListener(this);
		mDigits.setOnKeyListener(this);
		mDigits.setOnLongClickListener(this);
		mDigits.addTextChangedListener(this);
		/*
		 * PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity()
		 * , mDigits);
		 */
		// Check for the presence of the keypad
		View oneButton = fragmentView.findViewById(R.id.one);
		if (oneButton != null) {
			setupKeypad(fragmentView);
		}

		mDelete = fragmentView.findViewById(R.id.deleteButton);
		if (mDelete != null) {
			mDelete.setOnClickListener(this);
			mDelete.setOnLongClickListener(this);
		}

		mDialButton = fragmentView.findViewById(R.id.dialButton);

		if (mDialButton != null) {
			mDialButton.setOnClickListener(this);
		}

		mSpacer = fragmentView.findViewById(R.id.spacer);
		mSpacer.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (isDigitsEmpty()) {
					// hideAndClearDialpad();
					return true;
				}
				return false;
			}
		});

		mDialpad = fragmentView.findViewById(R.id.dialpad); // This is null in
															// landscape mode.

		// In landscape we put the keyboard in phone mode.
		if (null == mDialpad) {
			mDigits.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
		} else {
			mDigits.setCursorVisible(false);
		}

		return fragmentView;
	}

	@Override
	public void onStop() {
		super.onStop();

		if (mClearDigitsOnStop) {
			mClearDigitsOnStop = false;
			mDigits.getText().clear();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		// Make sure we don't leave this activity with a tone still playing.
		stopTone();
		synchronized (mToneGeneratorLock) {
			if (mToneGenerator != null) {
				mToneGenerator.release();
				mToneGenerator = null;
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		final ContentResolver contentResolver = getActivity()
				.getContentResolver();

		mDTMFToneEnabled = Settings.System.getInt(contentResolver,
				Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

		synchronized (mToneGeneratorLock) {
			if (mToneGenerator == null) {
				try {
					mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE,
							TONE_RELATIVE_VOLUME);
				} catch (RuntimeException e) {
					Log.w(TAG,
							"Exception caught while creating local tone generator: "
									+ e);
					mToneGenerator = null;
				}
			}
		}
	}

	/**
	 * @return true if the widget with the phone number digits is empty.
	 */
	private boolean isDigitsEmpty() {
		return mDigits.length() == 0;
	}

	private void setupKeypad(View fragmentView) {
		final int[] buttonIds = new int[] { R.id.zero, R.id.one, R.id.two,
				R.id.three, R.id.four, R.id.five, R.id.six, R.id.seven,
				R.id.eight, R.id.nine, R.id.star, R.id.pound };

		final int[] numberIds = new int[] { R.string.dialpad_0_number,
				R.string.dialpad_1_number, R.string.dialpad_2_number,
				R.string.dialpad_3_number, R.string.dialpad_4_number,
				R.string.dialpad_5_number, R.string.dialpad_6_number,
				R.string.dialpad_7_number, R.string.dialpad_8_number,
				R.string.dialpad_9_number, R.string.dialpad_star_number,
				R.string.dialpad_pound_number };

		final int[] letterIds = new int[] { R.string.dialpad_0_letters,
				R.string.dialpad_1_letters, R.string.dialpad_2_letters,
				R.string.dialpad_3_letters, R.string.dialpad_4_letters,
				R.string.dialpad_5_letters, R.string.dialpad_6_letters,
				R.string.dialpad_7_letters, R.string.dialpad_8_letters,
				R.string.dialpad_9_letters, R.string.dialpad_star_letters,
				R.string.dialpad_pound_letters };

		final Resources resources = getResources();

		DialpadKeyButton dialpadKey;
		TextView numberView;
		TextView lettersView;

		for (int i = 0; i < buttonIds.length; i++) {
			dialpadKey = (DialpadKeyButton) fragmentView
					.findViewById(buttonIds[i]);
			dialpadKey.setLayoutParams(new TableRow.LayoutParams(
					TableRow.LayoutParams.MATCH_PARENT,
					TableRow.LayoutParams.MATCH_PARENT));
			dialpadKey.setOnPressedListener(this);
			numberView = (TextView) dialpadKey
					.findViewById(R.id.dialpad_key_number);
			lettersView = (TextView) dialpadKey
					.findViewById(R.id.dialpad_key_letters);
			final String numberString = resources.getString(numberIds[i]);
			numberView.setText(numberString);
			dialpadKey.setContentDescription(numberString);
			if (lettersView != null) {
				lettersView.setText(resources.getString(letterIds[i]));
				if (buttonIds[i] == R.id.zero) {
					lettersView
							.setTextSize(
									TypedValue.COMPLEX_UNIT_PX,
									resources
											.getDimension(R.dimen.dialpad_key_plus_size));
				}
			}
		}

		// Long-pressing one button will initiate Voicemail.
		fragmentView.findViewById(R.id.one).setOnLongClickListener(this);

		// Long-pressing zero button will enter '+' instead.
		fragmentView.findViewById(R.id.zero).setOnLongClickListener(this);

	}

	/**
	 * In most cases, when the dial button is pressed, there is a number in
	 * digits area. Pack it in the intent, start the outgoing call broadcast as
	 * a separate task and finish this activity.
	 * 
	 * When there is no digit and the phone is CDMA and off hook, we're sending
	 * a blank flash for CDMA. CDMA networks use Flash messages when special
	 * processing needs to be done, mainly for 3-way or call waiting scenarios.
	 * Presumably, here we're in a special 3-way scenario where the network
	 * needs a blank flash before being able to add the new participant. (This
	 * is not the case with all 3-way calls, just certain CDMA infrastructures.)
	 * 
	 * Otherwise, there is no digit, display the last dialed number. Don't
	 * finish since the user may want to edit it. The user needs to press the
	 * dial button again, to dial it (general case described above).
	 */
	public void dialButtonPressed() {
        if(!showAlertOnNoData(getActivity())){
            return;
        }
		if (isDigitsEmpty()) { // No number entered.
            Toast.makeText(getActivity(), getString(R.string.CreateAccountActivity_you_must_specify_a_phone_number), Toast.LENGTH_SHORT).show();
        } else {
			final String number = mDigits.getText().toString();

            if(number.contains("*") || number.contains("#")){
                Util.showAlertDialog(getActivity(),
                        getString(R.string.CreateAccountActivity_invalid_number),
                        String.format(getString(R.string.CreateAccountActivity_the_number_you_specified_s_is_invalid), number));
                return;
            }

			if (number != null) {
				Intent intent = new Intent(getActivity(), RedPhoneService.class);
				intent.setAction(RedPhoneService.ACTION_OUTGOING_CALL);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.putExtra(Constants.REMOTE_NUMBER, number);
				getActivity().getApplication().startService(intent);

				Intent activityIntent = new Intent(getActivity(),
						RedPhone.class);
				activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(activityIntent);

				getActivity().finish();
			}
		}
	}

	@Override
	public boolean onKey(View view, int keyCode, KeyEvent event) {

		switch (view.getId()) {
		case R.id.digits:
			if (keyCode == KeyEvent.KEYCODE_ENTER) {
				dialButtonPressed();
				return true;
			}
			break;
		}
		return false;

	}

	/**
	 * Remove the digit just before the current position. This can be used if we
	 * want to replace the previous digit or cancel previously entered
	 * character.
	 */
	private void removePreviousDigitIfPossible() {
		final Editable editable = mDigits.getText();
		final int currentPosition = mDigits.getSelectionStart();
		if (currentPosition > 0) {
			mDigits.setSelection(currentPosition);
			mDigits.getText().delete(currentPosition - 1, currentPosition);
		}
	}

	/**
	 * Stop the tone if it is played.
	 */
	private void stopTone() {
		// if local tone playback is disabled, just return.
		if (!mDTMFToneEnabled) {
			return;
		}
		synchronized (mToneGeneratorLock) {
			if (mToneGenerator == null) {
				Log.w(TAG, "stopTone: mToneGenerator == null");
				return;
			}
			mToneGenerator.stopTone();
		}
	}

	@Override
	public boolean onLongClick(View view) {

		final Editable digits = mDigits.getText();
		final int id = view.getId();
		switch (id) {
		case R.id.deleteButton: {
			digits.clear();
			mDelete.setPressed(false);
			return true;
		}
		case R.id.one: {
			if (isDigitsEmpty() || TextUtils.equals(mDigits.getText(), "1")) {
				removePreviousDigitIfPossible();
				return true;
			}
			return false;
		}
		case R.id.zero: {
			// Remove tentative input ('0') done by onTouch().
			removePreviousDigitIfPossible();
			keyPressed(KeyEvent.KEYCODE_PLUS);

			// Stop tone immediately
			stopTone();
			mPressedDialpadKeys.remove(view);

			return true;
		}
		case R.id.digits: {
			mDigits.setCursorVisible(true);
			return false;
		}

		}
		return false;
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.deleteButton: {
			keyPressed(KeyEvent.KEYCODE_DEL);
			return;
		}
		case R.id.dialButton: {
			dialButtonPressed();
			return;
		}
		case R.id.digits: {
			if (!isDigitsEmpty()) {
				mDigits.setCursorVisible(true);
			}
			return;
		}
		default: {
			Log.wtf(TAG, "Unexpected onClick() event from: " + view);
			return;
		}
		}
	}

	/**
	 * Play the specified tone for the specified milliseconds
	 * 
	 * The tone is played locally, using the audio stream for phone calls. Tones
	 * are played only if the "Audible touch tones" user preference is checked,
	 * and are NOT played if the device is in silent mode.
	 * 
	 * The tone length can be -1, meaning "keep playing the tone." If the caller
	 * does so, it should call stopTone() afterward.
	 * 
	 * @param tone
	 *            a tone code from {@link ToneGenerator}
	 * @param durationMs
	 *            tone length.
	 */
	private void playTone(int tone, int durationMs) {

		// if local tone playback is disabled, just return.
		if (!mDTMFToneEnabled) {
			return;
		}

		AudioManager audioManager = (AudioManager) getActivity()
				.getSystemService(Context.AUDIO_SERVICE);
		int ringerMode = audioManager.getRingerMode();
		if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
				|| (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
			return;
		}
		synchronized (mToneGeneratorLock) {
			if (mToneGenerator == null) {
				Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
				return;
			}
			mToneGenerator.startTone(tone, durationMs);
		}
	}

	private void keyPressed(int keyCode) {
		if (getView().getTranslationY() != 0) {
			return;
		}
		switch (keyCode) {
		case KeyEvent.KEYCODE_1:
			playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_2:
			playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_3:
			playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_4:
			playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_5:
			playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_6:
			playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_7:
			playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_8:
			playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_9:
			playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_0:
			playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_POUND:
			playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_INFINITE);
			break;
		case KeyEvent.KEYCODE_STAR:
			playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_INFINITE);
			break;
		default:
			break;
		}

		KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
		mDigits.onKeyDown(keyCode, event);

		// If the cursor is at the end of the text we hide it.
		final int length = mDigits.length();
		if (length == mDigits.getSelectionStart()
				&& length == mDigits.getSelectionEnd()) {
			mDigits.setCursorVisible(false);
		}
	}

	@Override
	public void onPressed(View view, boolean pressed) {
		if (pressed) {
			switch (view.getId()) {
			case R.id.one: {
				keyPressed(KeyEvent.KEYCODE_1);
				break;
			}
			case R.id.two: {
				keyPressed(KeyEvent.KEYCODE_2);
				break;
			}
			case R.id.three: {
				keyPressed(KeyEvent.KEYCODE_3);
				break;
			}
			case R.id.four: {
				keyPressed(KeyEvent.KEYCODE_4);
				break;
			}
			case R.id.five: {
				keyPressed(KeyEvent.KEYCODE_5);
				break;
			}
			case R.id.six: {
				keyPressed(KeyEvent.KEYCODE_6);
				break;
			}
			case R.id.seven: {
				keyPressed(KeyEvent.KEYCODE_7);
				break;
			}
			case R.id.eight: {
				keyPressed(KeyEvent.KEYCODE_8);
				break;
			}
			case R.id.nine: {
				keyPressed(KeyEvent.KEYCODE_9);
				break;
			}
			case R.id.zero: {
				keyPressed(KeyEvent.KEYCODE_0);
				break;
			}
			case R.id.pound: {
				keyPressed(KeyEvent.KEYCODE_POUND);
				break;
			}
			case R.id.star: {
				keyPressed(KeyEvent.KEYCODE_STAR);
				break;
			}
			default: {
				Log.wtf(TAG, "Unexpected onTouch(ACTION_DOWN) event from: "
						+ view);
				break;
			}
			}
			mPressedDialpadKeys.add(view);
		} else {
			view.jumpDrawablesToCurrentState();
			mPressedDialpadKeys.remove(view);

			if (mPressedDialpadKeys.isEmpty()) {
				stopTone();
			}

		}
	}

	/**
	 * Plays the specified tone for TONE_LENGTH_MS milliseconds.
	 */
	private void playTone(int tone) {
		playTone(tone, TONE_LENGTH_MS);
	}

	@Override
	public void afterTextChanged(Editable s) {
		if (isDigitsEmpty()) {
			mDigitsFilledByIntent = false;
			mDigits.setCursorVisible(false);
		}

		if (mDialpadQueryListener != null) {
			mDialpadQueryListener.onDialpadQueryChanged(mDigits.getText()
					.toString());
		}
		updateDialAndDeleteButtonEnabledState();
	}

	/**
	 * Update the enabledness of the "Dial" and "Backspace" buttons if
	 * applicable.
	 */
	private void updateDialAndDeleteButtonEnabledState() {
		final boolean digitsNotEmpty = !isDigitsEmpty();
		mDelete.setEnabled(digitsNotEmpty);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
	}

	@Override
	public void onTextChanged(CharSequence input, int start, int before,
			int count) {
		if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
			final Activity activity = getActivity();
			if (activity != null) {
				activity.invalidateOptionsMenu();
			}
		}
	}
}
