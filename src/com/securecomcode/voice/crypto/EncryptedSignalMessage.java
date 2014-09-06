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

package com.securecomcode.voice.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.securecomcode.voice.Constants;
import com.securecomcode.voice.util.Base64;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A class representing an encrypted "signal," typically a notification of
 * an incoming call that's delivered over either SMS or C2DM.
 *
 * Message format is $IV:ciphertext:MAC
 *
 * Version   : 1 Byte
 * IV        : Random.
 * Ciphertext: AES-128 encrypted with CBC mode.
 * MAC       : Hmac-SHA1, truncated to 80 bits over everything preceding (encrypted then auth).

 * @author Moxie Marlinspike
 *
 */

public class EncryptedSignalMessage {

  private static final int VERSION_OFFSET    = 0;
  private static final int IV_OFFSET         = VERSION_OFFSET + 1;
  private static final int CIPHERTEXT_OFFSET = IV_OFFSET + 16;
  private static final int MAC_LENGTH        = 10;

  private final String message;
  private final Context context;
  private final char[] hexArray = "0123456789ABCDEF".toCharArray();

  public EncryptedSignalMessage(Context context, String message) {
    this.message = message;
    this.context = context.getApplicationContext();
  }

  private byte[] getCombinedKey() throws InvalidEncryptedSignalException, IOException {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    String key                    = preferences.getString(Constants.KEY_PREFERENCE, null);
    if (key == null)
      throw new InvalidEncryptedSignalException("No combined key available!");

    byte[] keyBytes = Base64.decode(key);

    if (keyBytes.length != 40)
      throw new InvalidEncryptedSignalException("Local cipher+mac key != 40 bytes?");

    return keyBytes;
  }

  private byte[] getCipherKey() throws InvalidEncryptedSignalException, IOException {
    byte[] keyBytes       = getCombinedKey();
    byte[] cipherKeyBytes = new byte[16];

    System.arraycopy(keyBytes, 0, cipherKeyBytes, 0, cipherKeyBytes.length);
    return cipherKeyBytes;
  }

  private byte[] getMacKey() throws InvalidEncryptedSignalException, IOException {
    byte[] keyBytes    = getCombinedKey();
    byte[] macKeyBytes = new byte[20];

    System.arraycopy(keyBytes, 16, macKeyBytes, 0, macKeyBytes.length);
    return macKeyBytes;
  }

  private boolean verifyMac(byte[] messageBytes)
      throws InvalidEncryptedSignalException, IOException,
             NoSuchAlgorithmException, InvalidKeyException
  {
    byte[] macKey     = getMacKey();
    SecretKeySpec key = new SecretKeySpec(macKey, "HmacSHA1");
    Mac mac           = Mac.getInstance("HmacSHA1");

    mac.init(key);
    mac.update(messageBytes, 0, messageBytes.length-MAC_LENGTH);

    byte[] ourDigestComplete = mac.doFinal();
    byte[] ourDigest         = new byte[10];
    byte[] theirDigest       = new byte[10];

    System.arraycopy(ourDigestComplete,  10, ourDigest, 0, ourDigest.length);
    System.arraycopy(messageBytes, messageBytes.length-MAC_LENGTH,
                     theirDigest, 0, theirDigest.length);

    return Arrays.equals(ourDigest, theirDigest);
  }

    public String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

  private boolean isValidVersion(byte[] messageBytes) {
    return messageBytes[VERSION_OFFSET] == 0x00;
  }

  private Cipher getCipher(byte[] messageBytes)
      throws InvalidEncryptedSignalException, InvalidKeyException,
             InvalidAlgorithmParameterException, NoSuchAlgorithmException,
             NoSuchPaddingException, IOException
  {
    SecretKeySpec cipherKey = new SecretKeySpec(getCipherKey(), "AES");
    byte[] ivBytes          = new byte[16];

    if (messageBytes.length < ivBytes.length)
      throw new InvalidEncryptedSignalException("Message shorter than IV length.");

    System.arraycopy(messageBytes, IV_OFFSET, ivBytes, 0, ivBytes.length);

    Cipher cipher      = Cipher.getInstance("AES/CBC/PKCS5Padding");
    IvParameterSpec iv = new IvParameterSpec(ivBytes);
    cipher.init(Cipher.DECRYPT_MODE, cipherKey, iv);

    return cipher;
  }

  public byte[] getPlaintext() throws InvalidEncryptedSignalException {
    try {

      byte[] messageBytes = Base64.decode(this.message);

      if (!isValidVersion(messageBytes))
        throw new InvalidEncryptedSignalException("Unknown version: " +
                                                  (byte)messageBytes[VERSION_OFFSET]);

      if (!verifyMac(messageBytes))
        throw new InvalidEncryptedSignalException("Bad MAC");

      Cipher cipher = getCipher(messageBytes);
      return cipher.doFinal(messageBytes, CIPHERTEXT_OFFSET,
                            messageBytes.length - CIPHERTEXT_OFFSET - MAC_LENGTH);
    } catch (IOException e) {
      throw new InvalidEncryptedSignalException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new InvalidEncryptedSignalException(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new InvalidEncryptedSignalException(e);
    } catch (BadPaddingException e) {
      throw new InvalidEncryptedSignalException(e);
    }
  }

}
