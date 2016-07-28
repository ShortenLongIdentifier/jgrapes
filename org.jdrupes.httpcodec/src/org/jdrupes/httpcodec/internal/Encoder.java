/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2016  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package org.jdrupes.httpcodec.internal;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Stack;

import org.jdrupes.httpcodec.fields.HttpContentLengthField;
import org.jdrupes.httpcodec.fields.HttpDateField;
import org.jdrupes.httpcodec.fields.HttpField;
import org.jdrupes.httpcodec.fields.HttpIntField;
import org.jdrupes.httpcodec.fields.HttpMediaTypeField;
import org.jdrupes.httpcodec.fields.HttpStringListField;
import org.jdrupes.httpcodec.util.ByteBufferOutputStream;
import org.jdrupes.httpcodec.util.ByteBufferUtils;

/**
 * @author Michael N. Lipp
 *
 */
public abstract class Encoder<T extends MessageHeader> extends Codec<T> {

	private enum State {
		// Main states
		INITIAL, DONE, CLOSED,
		// Sub states
		HEADERS, CHUNK_BODY, FINISH_CHUNK, FINISH_CHUNKED,
		START_COLLECT_BODY, COLLECT_BODY, STREAM_COLLECTED, 
		STREAM_BODY
	}

	private Stack<State> states = new Stack<>();
	private boolean closeAfterBody = false;
	private ByteBufferOutputStream outStream;
	private Writer writer;
	private Iterator<HttpField<?>> headerIter = null;
	private int pendingLimit = 1024 * 1024;
	private long leftToStream;
	private ByteBufferOutputStream collectedBodyData;

	/**
	 * Creates a new encoder.
	 */
	public Encoder() {
		outStream = new ByteBufferOutputStream();
		try {
			writer = new OutputStreamWriter(outStream, "ascii");
		} catch (UnsupportedEncodingException e) {
		}
		states.push(State.INITIAL);
	}

	/**
	 * Returns the limit for pending body bytes. If the protocol is HTTP/1.0 and
	 * the message has a body but no "Content-Length" header, the only
	 * (unreliable) way to indicate the end of the body is to close the
	 * connection after all body bytes have been sent.
	 * <P>
	 * The encoder tries to calculate the content length by buffering the body
	 * data up to the "pending" limit. If the body is smaller than the limit,
	 * the message is set with the calculated content length header, else the
	 * data is sent without such a header and the connection is closed.
	 * <P>
	 * If the response protocol is HTTP/1.1 and there is no "Content-Length"
	 * header, chunked transfer encoding is used.
	 *
	 * @return the limit
	 */
	public int getPendingLimit() {
		return pendingLimit;
	}

	/**
	 * Sets the limit for the pending body bytes.
	 * 
	 * @param pendingLimit
	 *            the limit to set
	 */
	public void setPendingLimit(int pendingLimit) {
		this.pendingLimit = pendingLimit;
	}

	/**
	 * Returns {@code true} if the encoder does not accept further input because
	 * the processed data indicated that the connection has been or is to be
	 * closed.
	 * 
	 * @return the result
	 */
	public boolean isClosed() {
		return states.peek() == State.CLOSED;
	}

	/**
	 * Writes the first line of the message (including the terminating CRLF).
	 * Must be provided by the derived class because the first line depends on
	 * whether a request or response is encoded.
	 * 
	 * @param messageHeader
	 *            the message header to encode (see
	 *            {@link #encode(MessageHeader)}
	 * @param writer
	 *            the Writer to use for writing
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	protected abstract void startMessage(T messageHeader, Writer writer)
	        throws IOException;

	/**
	 * Factory method to be implemented by derived classes that returns an
	 * encoder result as appropriate for the encoder.
	 * 
	 * @param overflow
	 *            {@code true} if the data didn't fit in the out buffer
	 * @param underflow
	 *            {@code true} if more data is expected
	 */
	protected abstract CodecResult newResult(boolean overflow,
	        boolean underflow);

	/**
	 * Set a new HTTP message that is to be encoded.
	 * 
	 * @param messageHeader
	 *            the response
	 */
	public void encode(T messageHeader) {
		if (states.peek() != State.INITIAL) {
			throw new IllegalStateException();
		}
		this.messageHeader = messageHeader;
	}

	/**
	 * Convenience method for invoking
	 * {@link #encode(ByteBuffer, ByteBuffer, boolean)} with an empty {@code in}
	 * buffer and {@code true}. Can be used to get the result of encoding a 
	 * message without body.
	 * 
	 * @param out
	 *            the buffer to which data is written
	 * @return the result
	 */
	public CodecResult encode(ByteBuffer out) {
		return encode(EMPTY_IN, out, true);
	}

	private final static CodecResult CONTINUE = new CodecResult(false, false);
	
	/**
	 * Encodes a HTTP message.
	 * 
	 * @param in
	 *            the body data
	 * @param out
	 *            the buffer to which data is written
	 * @param endOfInput
	 *            {@code true} if there is no input left beyond the data
	 *            currently in the {@code in} buffer (indicates end of body or
	 *            no body at all)
	 * @return the result
	 */
	public CodecResult encode(ByteBuffer in, ByteBuffer out,
	        boolean endOfInput) {
		outStream.assignBuffer(out);
		CodecResult result = CONTINUE;
		if (out.remaining() == 0) {
			return newResult(true, false);
		}
		while (true) {
			if (result.isOverflow() || result.isUnderflow()) {
				return result;
			}
			switch (states.peek()) {
			case INITIAL:
				startMessage();
				break;

			case HEADERS:
				// If headers remain (new request or buffer was full) write them
				if (writeHeaders()) {
					continue;
				}
				break;

			case START_COLLECT_BODY:
				// Start collecting
				if (in.remaining() == 0 && !endOfInput) {
					// Has probably been invoked with a dummy buffer,
					// cannot be used to create pending body buffer.
					return newResult(false, true);
				}
				collectedBodyData = new ByteBufferOutputStream(
				        Math.max(in.capacity(), 64 * 1024));
				states.pop();
				states.push(State.COLLECT_BODY);
				// fall through (no write occurred yet)
			case COLLECT_BODY:
				result = collectBody(in, endOfInput);
				continue; // never writes to out

			case STREAM_COLLECTED:
				// Output collected body
				if (collectedBodyData.remaining() < 0) {
					collectedBodyData.assignBuffer(out);
					break;
				}
				states.pop();
				continue;

			case STREAM_BODY:
				// Stream, i.e. simply copy from source to destination
				int initiallyRemaining = in.remaining();
				if (out.remaining() <= leftToStream) {
					ByteBufferUtils.putAsMuchAsPossible(out, in);
				} else {
					ByteBufferUtils.putAsMuchAsPossible(out, in,
					        (int) leftToStream);
				}
				leftToStream -= (initiallyRemaining - in.remaining());
				if (leftToStream == 0 || endOfInput) {
					// end of data
					states.pop();
					continue;
				}
				// Everything written, waiting for more data or end of data
				return newResult(out.remaining() == 0, in.remaining() == 0);

			case CHUNK_BODY:
				// Send in data as chunk
				result = startChunk(in, out, endOfInput);
				continue; // remaining check already done 

			case FINISH_CHUNK:
				try {
					outStream.write("\r\n".getBytes("ascii"));
					states.pop();
				} catch (IOException e) {
					// Formally thrown by write
				}
				break;
				
			case FINISH_CHUNKED:
				try {
					outStream.write("0\r\n\r\n".getBytes("ascii"));
					states.pop();
				} catch (IOException e) {
					// Formally thrown by write
				}
				break;
				
			case DONE:
				// Everything is written
				if (!endOfInput) {
					if (in.remaining() == 0) {
						return newResult(false, true);
					}
					throw new IllegalStateException("Unexpected input.");
				}
				states.pop();
				if (closeAfterBody) {
					states.push(State.CLOSED);
				} else {
					states.push(State.INITIAL);
				}
				return newResult(false, false);

			default:
				throw new IllegalStateException();
			}
			// Using "continue" above avoids this check. Use it only
			// if the state has changed and no addition output is expected. 
			if (out.remaining() == 0) {
				return newResult(true, false);
			}
		}
	}

	/**
	 * Called during the initial state. Writes the status line, determines the
	 * body-mode and puts the state machine in output-headers mode, unless the
	 * body-mode is "collect body".
	 */
	private void startMessage() {
		// Make sure we have a Date, RFC 7231 7.1.1.2
		if (!messageHeader.fields().containsKey(HttpField.DATE)) {
			messageHeader.setField(new HttpDateField());
		}

		// Complete content type
		HttpMediaTypeField contentType = (HttpMediaTypeField) messageHeader
		        .fields().get(HttpField.CONTENT_TYPE);
		String charset = null;
		if (contentType != null) {
			charset = contentType.getParameter("charset");
			if (charset == null) {
				charset = "utf-8";
				contentType.setParameter("charset", charset);
			}
		}
		
		// Prepare encoder
		outStream.clear();
		headerIter = null;

		// Write request or status line
		try {
			startMessage(messageHeader, writer);
		} catch (IOException e) {
			// Formally thrown by writer, cannot happen.
		}
		states.pop();
		// We'll eventually fall back to this state
		states.push(State.DONE);
		// Get a default for closeAfterBody from the header fields
		HttpStringListField conField = messageHeader
		        .getField(HttpStringListField.class, HttpField.CONNECTION);
		closeAfterBody = conField != null
		        && conField.containsIgnoreCase("close");
		// If there's no body, start outputting header fields
		if (!messageHeader.messageHasBody()) {
			states.push(State.HEADERS);
			return;
		}
		// Message has a body, find out how to handle it
		HttpIntField cl = messageHeader.getField(HttpIntField.class,
		        HttpField.CONTENT_LENGTH);
		leftToStream = (cl == null ? -1 : cl.getValue());
		if (leftToStream >= 0) {
			// Easiest: we have a content length, works always
			states.push(State.STREAM_BODY);
			states.push(State.HEADERS);
			return;
		} 
		if (messageHeader.getProtocol()
		        .compareTo(HttpProtocol.HTTP_1_0) > 0) {
			// At least 1.1, use chunked
			HttpStringListField transEnc = messageHeader.getField(
			        HttpStringListField.class, HttpField.TRANSFER_ENCODING);
			if (transEnc == null) {
				messageHeader.setField(new HttpStringListField(
				        HttpField.TRANSFER_ENCODING,
				        TransferCoding.CHUNKED.toString()));
			} else {
				transEnc.remove(TransferCoding.CHUNKED.toString());
				transEnc.add(TransferCoding.CHUNKED.toString());
			}
			states.push(State.CHUNK_BODY);
			states.push(State.HEADERS);
			return;
		}
		// Bad: 1.0 and no content length.
		if (pendingLimit > 0) {
			// Try to calculate length by collecting the data
			leftToStream = 0;
			states.push(State.START_COLLECT_BODY);
			return;
		}
		// May not buffer, use close
		states.push(State.STREAM_BODY);
		states.push(State.HEADERS);
		closeAfterBody = true;
	}

	/**
	 * Outputs as many headers as fit in the current out buffer. If all headers
	 * are output, pops a state (thus proceeding with the selected body mode).
	 * Unless very small out buffers are used (or very large headers occur),
	 * this is invoked only once. Therefore no attempt has been made to avoid
	 * the usage of temporary buffers in the header header stream (there may be
	 * a maximum overflow of one partial header).
	 * 
	 * @return {@code true} if all headers could be written to the out
	 * buffer (nothing pending)
	 */
	private boolean writeHeaders() {
		try {
			if (headerIter == null) {
				headerIter = messageHeader.fields().values().iterator();
			}
			while (true) {
				if (!headerIter.hasNext()) {
					writer.write("\r\n");
					writer.flush();
					states.pop();
					return outStream.remaining() >= 0;
				}
				HttpField<?> header = headerIter.next();
				writer.write(header.asHeaderField());
				writer.write("\r\n");
				writer.flush();
				if (outStream.remaining() <= 0) {
					break;
				}
			}
		} catch (IOException e) {
			// Formally thrown by writer, cannot happen.
		}
		return false;
	}

	/**
	 * Handle the input as appropriate for the collect-body mode.
	 * 
	 * @param in
	 * @param endOfInput
	 */
	private CodecResult collectBody(ByteBuffer in, boolean endOfInput) {
		if (collectedBodyData.remaining() + in.remaining() > pendingLimit) {
			// No space left, output headers, collected and rest (and then
			// close)
			states.pop();
			closeAfterBody = true;
			states.push(State.STREAM_BODY);
			states.push(State.STREAM_COLLECTED);
			states.push(State.HEADERS);
			return CONTINUE;
		}
		// Space left, collect
		collectedBodyData.write(in);
		if (endOfInput) {
			// End of body, found content length!
			messageHeader.setField(new HttpContentLengthField(
			        collectedBodyData.bytesWritten()));
			states.pop();
			states.push(State.STREAM_COLLECTED);
			states.push(State.HEADERS);
			return CONTINUE;
		}
		// Get more input
		return newResult(false, true);
	}

	/**
	 * Handle the input as appropriate for the chunked-body mode.
	 * 
	 * @param in
	 * @return
	 */
	private CodecResult startChunk(ByteBuffer in, ByteBuffer out,
	        boolean endOfInput) {
		if (endOfInput) {
			states.pop();
			states.push(State.FINISH_CHUNKED);
		}
		try {
			// Don't write zero sized chunks
			if (in.hasRemaining()) {
				leftToStream = in.remaining();
				outStream.write(
				        Long.toHexString(leftToStream).getBytes("ascii"));
				outStream.write("\r\n".getBytes("ascii"));
				states.push(State.FINISH_CHUNK);
				states.push(State.STREAM_BODY);
				return newResult(outStream.remaining() <= 0, false);
			}
		} catch (IOException e) {
			// Formally thrown by outStream, cannot happen.
		}
		return newResult(outStream.remaining() < 0, 
				in.remaining() == 0 && !endOfInput);
	}
}
