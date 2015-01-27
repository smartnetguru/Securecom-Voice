/**
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
package com.securecomcode.voice.gcm;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Switch;

import com.securecomcode.voice.signaling.SignalingSocket;
import com.google.android.gcm.GCMBaseIntentService;
import com.google.protobuf.InvalidProtocolBufferException;

import com.securecomcode.voice.Constants;
import com.securecomcode.voice.RedPhoneService;
import com.securecomcode.voice.crypto.EncryptedSignalMessage;
import com.securecomcode.voice.crypto.InvalidEncryptedSignalException;
import com.securecomcode.voice.signaling.SessionDescriptor;
import com.securecomcode.voice.signaling.signals.CompressedInitiateSignalProtocol.CompressedInitiateSignal;
import com.securecomcode.voice.sms.IncomingCallDetails;

public class GCMIntentService extends GCMBaseIntentService {

    public GCMIntentService() {
        super(GCMRegistrationService.GCM_SENDER_ID);
    }

    @Override
    protected void onRegistered(Context context, String registrationId) {
        Log.w("GCMIntentService", "GCM Registered!");
        GCMRegistrarHelper.setRegistrationIdOnServer(context, registrationId);
    }

    @Override
    protected void onUnregistered(Context context, String registrationId) {
        Log.w("GCMIntentService", "GCM Unregistered!");
        GCMRegistrarHelper.unsetRegistrationIdOnServer(context, registrationId);
    }

    @Override
    protected void onError(Context context, String error) {
        Log.w("GCMIntentService", "GCM Registration failed with hard error: " + error);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
        String data = intent.getStringExtra("message");
        String signal = "";


        IncomingCallDetails callDetails = getIncomingCallDetails(context, data);

        if (callDetails != null) {
                intent.setClass(context, RedPhoneService.class);
                intent.setAction(RedPhoneService.ACTION_INCOMING_CALL);
                intent.putExtra(Constants.REMOTE_NUMBER, callDetails.getInitiator());
                intent.putExtra(Constants.SESSION, new SessionDescriptor(callDetails.getHost(),
                        callDetails.getIP(),
                        callDetails.getPort(),
                        callDetails.getSessionId(),
                        callDetails.getVersion()));
                context.startService(intent);
        }
    }

    private IncomingCallDetails getIncomingCallDetails(Context context, String signalString) {
        try {
            Log.w("GCMIntentService", "Got GCM Signal: " + signalString);
            EncryptedSignalMessage encryptedSignalMessage = new EncryptedSignalMessage(context,
                    signalString);
            CompressedInitiateSignal signal = CompressedInitiateSignal.parseFrom(encryptedSignalMessage
                    .getPlaintext());

            return new IncomingCallDetails(signal.getInitiator(), signal.getPort(),
                    signal.getSessionId(), signal.getServerName(),
                    signal.getVersion(),
                    signal.getServerIP());
        } catch (InvalidEncryptedSignalException e) {
            Log.w("GCMIntentService", e);
            return null;
        } catch (InvalidProtocolBufferException e) {
            Log.w("GCMIntentService", e);
            return null;
        }
    }
}
