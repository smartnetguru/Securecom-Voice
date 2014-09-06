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

package com.securecomcode.voice.signaling.signals;

import com.securecomcode.voice.contacts.ContactTokenList;
import com.google.thoughtcrimegson.Gson;

import java.util.Set;
import android.util.Log;

/**
 * A signal that requests a verification code via SMS
 * in order to initiate the account creation process.
 *
 * @author Moxie Marlinspike
 */

public class GetContactsSignal extends Signal {

    private final ContactTokenList contacts;

    public GetContactsSignal(String localNumber, String password, ContactTokenList contacts) {
        super(localNumber, password, -1);
        this.contacts = contacts;
    }

    @Override
    protected String getMethod() {
        return "PUT";
    }

    @Override
    protected String getLocation() {
        return "/v1/directory/tokens";/*"/users/directory";*/
    }

    @Override
    protected String getBody() {
        Gson gson = new Gson();
        String list = gson.toJson(/*new GetContactList(*/contacts/*)*/);
        return list;
    }

    /*private static class GetContactList {
        public Set<String> contacts;

        public GetContactList(Set<String> contacts) {
            this.contacts = contacts;
        }
    }*/
}
