package org.jdrupes.httpcodec.test;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.jdrupes.httpcodec.DecoderResult;
import org.jdrupes.httpcodec.HttpRequestDecoder;
import org.junit.Test;

public class GetRequestsTests {

	@Test
	public void testSplit() {
		String reqText 
			= "GET /test HTTP/1.1\r\n"
			+ "Host: local";
		ByteBuffer buffer = ByteBuffer.allocate(8192);
		buffer.put(reqText.getBytes());
		buffer.flip();
		HttpRequestDecoder decoder = new HttpRequestDecoder();
		DecoderResult result = decoder.decode(buffer);
		assertFalse(result.hasRequest());
		assertFalse(result.hasResponse());
		assertFalse(result.hasPayloadBytes());
		assertFalse(result.hasPayloadChars());
		assertFalse(result.getCloseConnection());
		reqText 
			= "host:8888\r\n"
			+ "\r\n";
		buffer.clear();
		buffer.put(reqText.getBytes());
		buffer.flip();
		result = decoder.decode(buffer);
		assertTrue(result.hasRequest());
		assertFalse(result.hasResponse());
		assertFalse(result.hasPayloadBytes());
		assertFalse(result.hasPayloadChars());
		assertFalse(result.getCloseConnection());
		assertEquals("GET", result.getRequest().getMethod());
		assertEquals("localhost", result.getRequest().getHost());
		assertEquals(8888, result.getRequest().getPort());
		assertEquals("/test", result.getRequest().getRequestUri().getPath());
	}
	
	@Test
	public void testGetRequest() {
		String reqText 
			= "GET /test HTTP/1.1\r\n"
			+ "Host: localhost:8888\r\n"
			+ "Connection: keep-alive\r\n"
			+ "Upgrade-Insecure-Requests: 1\r\n"
			+ "User-Agent: JUnit\r\n"
			+ "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,"
			+ "image/webp,*/*;q=0.8\r\n"
			+ "Accept-Encoding: gzip, deflate, sdch\r\n"
			+ "Accept-Language: de-DE,de;q=0.8,en-US;q=0.6,en;q=0.4\r\n"
			+ "Cookie: _test.; gsScrollPos=\r\n"
			+ "\r\n";
		ByteBuffer buffer = ByteBuffer.allocate(8192);
		buffer.put(reqText.getBytes());
		buffer.flip();
		HttpRequestDecoder decoder = new HttpRequestDecoder();
		DecoderResult result = decoder.decode(buffer);
		assertTrue(result.hasRequest());
		assertFalse(result.hasResponse());
		assertFalse(result.hasPayloadBytes());
		assertFalse(result.hasPayloadChars());
		assertFalse(result.getCloseConnection());
		assertEquals("GET", result.getRequest().getMethod());
		assertEquals("localhost", result.getRequest().getHost());
		assertEquals(8888, result.getRequest().getPort());
		assertEquals("/test", result.getRequest().getRequestUri().getPath());
	}

}
