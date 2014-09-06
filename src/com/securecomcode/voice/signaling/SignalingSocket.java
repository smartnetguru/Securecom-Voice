/*
 * Copyright (C) 2011 Whisper Systems]
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

package com.securecomcode.voice.signaling;

import android.content.Context;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.securecomcode.voice.contacts.ContactTokenDetails;
import com.securecomcode.voice.contacts.ContactTokenDetailsList;
import com.securecomcode.voice.contacts.ContactTokenList;
import com.securecomcode.voice.signaling.signals.GetContactsSignal;
import com.securecomcode.voice.sms.IncomingCallDetails;
import com.google.thoughtcrimegson.Gson;

import org.apache.http.conn.ssl.SSLSocketFactory;
import com.securecomcode.voice.Constants;
import com.securecomcode.voice.Release;
import com.securecomcode.voice.network.LowLatencySocketConnector;
import com.securecomcode.voice.signaling.signals.BusySignal;
import com.securecomcode.voice.signaling.signals.C2DMRegistrationSignal;
import com.securecomcode.voice.signaling.signals.C2DMUnregistrationSignal;
import com.securecomcode.voice.signaling.signals.DirectoryRequestSignal;
import com.securecomcode.voice.signaling.signals.GCMRegistrationSignal;
import com.securecomcode.voice.signaling.signals.GCMUnregistrationSignal;
import com.securecomcode.voice.signaling.signals.HangupSignal;
import com.securecomcode.voice.signaling.signals.InitiateSignal;
import com.securecomcode.voice.signaling.signals.RingingSignal;
import com.securecomcode.voice.signaling.signals.ServerSignal;
import com.securecomcode.voice.signaling.signals.Signal;
import com.securecomcode.voice.signaling.signals.SignalPreferenceSignal;
import com.securecomcode.voice.util.LineReader;
import com.securecomcode.voice.util.PhoneNumberFormatter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A socket that speaks the signaling protocol with a whisperswitch.
 *
 * The signaling protocol is very similar to a RESTful HTTP API, where every
 * request yields a corresponding response, and authorization is done through
 * an Authorization header.
 *
 * Like SIP, however, both endpoints are simultaneously server and client, issuing
 * requests and responses to each-other.
 *
 * Connections are persistent, and the signaling connection
 * for any ongoing call must remain open, otherwise the call will drop.
 *
 * @author Moxie Marlinspike
 *
 */

public class SignalingSocket {
  protected static final int    PROTOCOL_VERSION = 1;

  private   final Context context;
  private   final Socket socket;
  private   final String signalingHost;
  private   final int    signalingPort;
  private   boolean closed = false;

  protected final LineReader lineReader;
  protected final OutputStream outputStream;
  protected String localNumber = "";
  protected final String password;
  protected final OtpCounterProvider counterProvider;

  private boolean connectionAttemptComplete;
  private static boolean interupted = false;
  private static IncomingCallDetails signaldata;

  public SignalingSocket(Context context) throws SignalingException {
    this(context,
        Release.MASTER_SERVER_HOST,
        Release.SERVER_PORT,
        PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.NUMBER_PREFERENCE, "NO_SAVED_NUMBER!"),
        PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PASSWORD_PREFERENCE,  "NO_SAVED_PASSWORD!"),
        null);
  }

  public SignalingSocket(Context context, String host, int port,
                         String localNumber, String password,
                         OtpCounterProvider counterProvider)
      throws SignalingException
  {
    try {
      this.context                   = context.getApplicationContext();
      this.connectionAttemptComplete = false;
      this.signalingHost             = host;
      this.signalingPort             = port;
      this.socket                    = constructSSLSocket(context, signalingHost, signalingPort);
      this.outputStream              = this.socket.getOutputStream();
      this.lineReader                = new LineReader(socket.getInputStream());
      if(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Phone")){
          this.localNumber               = PhoneNumberFormatter.formatNumber(context, localNumber);
      }else if(PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.REG_OPTION_SELECTED, "").equalsIgnoreCase("Email")){
          this.localNumber               = localNumber;
      }

      this.password                  = password;
      this.counterProvider           = counterProvider;
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  private Socket constructSSLSocket(Context context, String host, int port)
      throws SignalingException
  {
    try {
      AssetManager assetManager       = context.getAssets();
      InputStream keyStoreInputStream = assetManager.open("whisper.store");
      KeyStore trustStore             = KeyStore.getInstance("BKS");

      trustStore.load(keyStoreInputStream, "whisper".toCharArray());

      SSLSocketFactory sslSocketFactory = new SSLSocketFactory(trustStore);

      if (Release.SSL) {
        sslSocketFactory.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
      } else {
        Log.w("SignalingSocket", "Disabling hostname verification...");
        sslSocketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
      }

      return timeoutHackConnect(sslSocketFactory, host, port);
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (KeyStoreException e) {
      throw new IllegalArgumentException(e);
    } catch (CertificateException e) {
      throw new IllegalArgumentException(e);
    } catch (KeyManagementException e) {
      throw new IllegalArgumentException(e);
    } catch (UnrecoverableKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private Socket timeoutHackConnect(SSLSocketFactory sslSocketFactory, String host, int port)
      throws IOException
  {
    InetAddress[] addresses      = InetAddress.getAllByName(host);
    Socket stagedSocket          = LowLatencySocketConnector.connect(addresses, port);

    Log.w("SignalingSocket", "Connected to: " + stagedSocket.getInetAddress().getHostAddress());

    SocketConnectMonitor monitor = new SocketConnectMonitor(stagedSocket);

    monitor.start();

    Socket result = sslSocketFactory.createSocket(stagedSocket, host, port, true);

    synchronized (this) {
      this.connectionAttemptComplete = true;
      notify();

      if (result.isConnected()) return result;
      else                      throw new IOException("Socket timed out before " +
                                                      "connection completed.");
    }
  }

  public void close() {
    try {
      this.outputStream.close();
      this.socket.getInputStream().close();
      this.socket.close();
      this.closed = true;
    } catch (IOException ioe) {}
  }

  public boolean isClosed(){
      return closed;
  }

  public SessionDescriptor initiateConnection(String remoteNumber)
      throws ServerMessageException, SignalingException,
             NoSuchUserException, LoginFailedException
  {
    sendSignal(new InitiateSignal(localNumber, password,
                                  counterProvider.getOtpCounter(context),
                                  remoteNumber));

    SignalResponse response = readSignalResponse();

    Gson gson = new Gson();

    String s = new String(response.getBody());

    switch (response.getStatusCode()) {
    case 404: throw new NoSuchUserException("No such redphone user.");
    case 402: throw new ServerMessageException(new String(response.getBody()));
    case 401: throw new LoginFailedException("Initiate threw 401");
    case 200: return gson.fromJson(new String(response.getBody()), SessionDescriptor.class);
    default:  throw new SignalingException("Unknown response: " + response.getStatusCode());
    }
  }

  public void setRinging(long sessionId)
      throws SignalingException, SessionStaleException, LoginFailedException
  {
    sendSignal(new RingingSignal(localNumber, password,
                                 counterProvider.getOtpCounter(context),
                                 sessionId));

    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 404: throw new SessionStaleException("No such session: " + sessionId);
    case 401: throw new LoginFailedException("Ringing threw 401");
    case 200: return;
    default:  throw new SignalingException("Unknown response: " + response.getStatusCode());
    }
  }

  public void setHangup(long sessionId) {
      try {
        sendSignal(new HangupSignal(localNumber, password,
                                  counterProvider.getOtpCounter(context),
                                  sessionId));
      readSignalResponse();
    } catch (SignalingException se) {}
  }


  public void setBusy(long sessionId) throws SignalingException {
    sendSignal(new BusySignal(localNumber, password,
                              counterProvider.getOtpCounter(context),
                              sessionId));
    readSignalResponse();
  }

  public void registerSignalingPreference(String preference) throws SignalingException {
    sendSignal(new SignalPreferenceSignal(localNumber, password, preference));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200: return;
    default: throw new SignalingException("Received error from server: " +
                                          new String(response.getBody()));
    }
  }

  public void registerGcm(String registrationId) throws SignalingException {
    sendSignal(new GCMRegistrationSignal(localNumber, password, registrationId));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200: return;
    default: throw new SignalingException("Received error from server: " +
                                          new String(response.getBody()));
    }
  }

  public void unregisterGcm(String registrationId) throws SignalingException {
    sendSignal(new GCMUnregistrationSignal(localNumber, password, registrationId));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200: return;
    default: throw new SignalingException("Received error from server: " +
                                          new String(response.getBody()));
    }
  }

  public void registerC2dm(String registrationId) throws SignalingException {
    sendSignal(new C2DMRegistrationSignal(localNumber, password, registrationId));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200: return;
    default:  throw new SignalingException("Received error from server: " +
                                           new String(response.getBody()));
    }
  }

  public void unregisterC2dm() throws SignalingException {
    sendSignal(new C2DMUnregistrationSignal(localNumber, password));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200: return;
    default:  throw new SignalingException("Received error from server: " +
                                           new String(response.getBody()));
    }
  }

    public List<ContactTokenDetails> retrieveDirectory(Set<String> contactTokens) throws SignalingException {
       ContactTokenList contactTokenList = new ContactTokenList(new LinkedList<String>(contactTokens));

        //String response = makeRequest(DIRECTORY_TOKENS_PATH, "PUT", new Gson().toJson(contactTokenList));

        sendSignal(new GetContactsSignal(localNumber, password, contactTokenList));

        SignalResponse response = readSignalResponse();

        ContactTokenDetailsList activeTokens = new Gson().fromJson(new String(response.getBody()), ContactTokenDetailsList.class);

        return activeTokens.getContacts();
    }



  public DirectoryResponse getNumberFilter() throws SignalingException {
    sendSignal(new DirectoryRequestSignal(localNumber, password));
    SignalResponse response = readSignalResponse();

    switch (response.getStatusCode()) {
    case 200:
      try {
        if (!response.getHeaders().containsKey("X-Hash-Count"))
          break;

        int hashCount = Integer.parseInt(response.getHeaders().get("X-Hash-Count"));

        Log.w("SignalingSocket", "Got directory response: " + hashCount +
                                 " , " + response.getBody());

        return new DirectoryResponse(hashCount, response.getBody());
      } catch (NumberFormatException nfe) {
        Log.w("SignalingSocket", nfe);
        break;
      }
    default:
      Log.w("SignalingSocket", "Unknown response from directory request: " +
                               response.getStatusCode());
    }

    return null;
  }

  public void sendOkResponse() throws SignalingException {
    try {
      if(!isClosed()){
          this.outputStream.write("HTTP/1.0 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes());
      }
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  public boolean isGCMInteruptAvailable(){
      return interupted;
  }
  public boolean waitForSignal() throws SignalingException {
    try {
      socket.setSoTimeout(500);
      return lineReader.waitForAvailable();
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    } finally {
      try {
        socket.setSoTimeout(0);
      } catch (SocketException e) {
        Log.w("SignalingSocket", e);
      }
    }
  }

  public ServerSignal readSignal() throws SignalingException {
    try {
      SignalReader signalReader = new SignalReader(lineReader);
      String[] request            = signalReader.readSignalRequest();
      Map<String, String> headers = signalReader.readSignalHeaders();
      byte[] body                 = signalReader.readSignalBody(headers);

      return new ServerSignal(request[0].trim(), request[1].trim(), body);
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  protected void sendSignal(Signal signal) throws SignalingException {
    try {
      Log.d("SignalingSocket", "Sending signal...");
      this.outputStream.write(signal.serialize().getBytes());
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  protected SignalResponse readSignalResponse() throws SignalingException {
    try {
      SignalResponseReader responseReader = new SignalResponseReader(lineReader);
      int responseCode            = responseReader.readSignalResponseCode();
      Map<String, String> headers = responseReader.readSignalHeaders();
      byte[] body                 = responseReader.readSignalBody(headers);

      return new SignalResponse(responseCode, headers, body);
    } catch (IOException ioe) {
      throw new SignalingException(ioe);
    }
  }

  private class SocketConnectMonitor extends Thread {
    private final Socket socket;

    public SocketConnectMonitor(Socket socket) {
      this.socket           = socket;
    }

    @Override
    public void run() {
      synchronized (SignalingSocket.this) {
        try {
          if (!SignalingSocket.this.connectionAttemptComplete) SignalingSocket.this.wait(10000);
          if (!SignalingSocket.this.connectionAttemptComplete) this.socket.close();
        } catch (IOException ioe) {
          Log.w("SignalingSocket", ioe);
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
      }
    }
  }
}