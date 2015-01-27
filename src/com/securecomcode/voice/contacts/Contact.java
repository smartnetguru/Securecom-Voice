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
package com.securecomcode.voice.contacts;

import java.util.HashSet;

public class Contact{
    private String contactName;
    private int personId;
    private HashSet<String> number;
    private boolean pushRegistered;


    public Contact(){
        this.number = new HashSet<String>();
    }

    public void setContactName(String contactName){
        this.contactName = contactName;
    }

    public String getContactName(){
        return this.contactName;
    }

    public void setPersonId(int id){
        this.personId = id;
    }

    public int getPersonId(){
        return this.personId;
    }

    public void setPhoneNumber(String number){
        if(number == null)
            return;
        this.number.add(number);
    }

    public HashSet<String> getPhoneNumber(){
        return this.number;
    }

    public void setPushRegistered(boolean registered){
        this.pushRegistered = registered;
    }

    public boolean isPushRegistered(){
        return this.pushRegistered;
    }
}