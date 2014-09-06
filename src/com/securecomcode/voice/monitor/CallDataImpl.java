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

package com.securecomcode.voice.monitor;

import android.content.Context;
import android.util.Log;
import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.GsonBuilder;
import com.google.thoughtcrimegson.stream.JsonWriter;
import com.securecomcode.voice.R;
import com.securecomcode.voice.monitor.stream.EncryptedOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.zip.GZIPOutputStream;

import static com.securecomcode.voice.monitor.stream.EncryptedStreamUtils.getPublicKeyFromResource;

/**
 * Data collected over the course of a call.  Streamed to the temp cache directory
 * as gzipped json.
 *
 * @author Stuart O. Anderson
 */
public class CallDataImpl implements CallData {
  private final Gson gson = new GsonBuilder().serializeNulls().create();
  private final JsonWriter writer;
  private final File jsonFile;
  private boolean finished = false;

  public CallDataImpl(Context context) throws IOException {
    try {
      PublicKey publicKey = getPublicKeyFromResource(context.getResources(), R.raw.call_metrics_public);

      File cacheSubdir = new File(context.getCacheDir(), "/calldata");
      cacheSubdir.mkdir();
      jsonFile = File.createTempFile("calldata", ".json.gz", cacheSubdir);
      Log.d("CallDataImpl", "Writing output to " + jsonFile.getAbsolutePath());

      BufferedOutputStream bufferedStream = new BufferedOutputStream(new FileOutputStream(jsonFile));
      OutputStream outputStream = new EncryptedOutputStream(bufferedStream, publicKey);
      GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
      writer = new JsonWriter(new OutputStreamWriter(gzipStream));
      writer.beginArray();
    } catch (InvalidKeySpecException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void putNominal(String name, Object value) {
    addEvent(new MonitoredEvent(name, value));
  }

  @Override
  public synchronized void addEvent(MonitoredEvent event) {
    if (finished) {
      Log.d("CallDataImpl", "Not logging event, already finished");
      return;
    }
    Log.d("CallDataImpl", "Adding Event: " + gson.toJson(event));
    gson.toJson(event, event.getClass(), writer);
  }

  /**
   * Finish writing the call data to disk and return the file where it is stored.
   * @return
   */
  @Override
  public synchronized File finish() throws IOException {
    finished = true;
    writer.endArray();
    writer.close();
    return jsonFile;
  }

  public static void clearCache(Context context) {
    File cacheDir = new File(context.getCacheDir(), "/calldata");
    File[] cacheFiles = cacheDir.listFiles();
    if (cacheFiles == null) {
      return;
    }
    for(File cacheFile : cacheFiles) {
      cacheFile.delete();
    }
  }
}
