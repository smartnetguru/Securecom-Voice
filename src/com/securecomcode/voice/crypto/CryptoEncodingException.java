package com.securecomcode.voice.crypto;

public class CryptoEncodingException extends Exception
{

	private static final long serialVersionUID = 1L;

	public CryptoEncodingException(String s)
	{
		super(s);
	}

	public CryptoEncodingException(Exception e)
	{
		super(e);
	}

}