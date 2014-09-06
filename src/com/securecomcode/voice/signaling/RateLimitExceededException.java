package com.securecomcode.voice.signaling;


public class RateLimitExceededException extends Throwable {
  public RateLimitExceededException(String s) {
    super(s);
  }
}
