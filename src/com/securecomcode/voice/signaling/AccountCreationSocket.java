/*
 * Copyright (C) 2011 Whisper Systems
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

package com.securecomcode.voice.signaling;

import android.content.Context;
import android.util.Log;

import com.securecomcode.voice.Release;
import com.securecomcode.voice.signaling.signals.ContactNumbersList;
import com.securecomcode.voice.signaling.signals.CreateAccountSignal;
import com.securecomcode.voice.signaling.signals.GetContactsSignal;
import com.securecomcode.voice.signaling.signals.VerifyAccountSignal;
import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A socket that can using the signaling protocol to create and verify
 * accounts.  Verification is done through an SMS send-back.
 *
 * @author Moxie Marlinspike
 */

public class AccountCreationSocket extends SignalingSocket {

    public AccountCreationSocket(Context context, String localNumber, String password)
            throws SignalingException {
        super(context, Release.MASTER_SERVER_HOST, Release.SERVER_PORT, localNumber, password, null);
    }

    public void createAccount(boolean voice)
            throws SignalingException, AccountCreationException, RateLimitExceededException {
        sendSignal(new CreateAccountSignal(localNumber, password, voice));
        SignalResponse response = readSignalResponse();

        switch (response.getStatusCode()) {
            case 200:
                return;
            case 413:
                throw new RateLimitExceededException("Rate limit exceeded.");
            default:
                throw new AccountCreationException("Account creation failed: " +
                        response.getStatusCode());
        }
    }

    public void verifyAccount(String challenge, String key)
            throws SignalingException, AccountCreationException, RateLimitExceededException {
        sendSignal(new VerifyAccountSignal(localNumber, password, challenge, key));
        SignalResponse response = readSignalResponse();

        switch (response.getStatusCode()) {
            case 200:
                return;
            case 413:
                throw new RateLimitExceededException("Verify rate exceeded!");
            default:
                throw new AccountCreationException("Account verification failed: " +
                        response.getStatusCode());
        }
    }

}
