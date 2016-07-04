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
package org.jdrupes.httpcodec.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * An {@link OutputStream} that is backed by a {@link ByteBuffer}
 * assigned to the stream. If the buffer becomes full, one or more
 * buffers are allocated as intermediate storage. Their content is
 * copied to the next assigned buffer(s).
 * <p>
 * While writing to this stream, {@link #remaining()} should be checked
 * regularly and the production of data should be suspended if possible
 * when no more space is left to avoid the usage of intermediate
 * storage.
 * 
 * @author Michael N. Lipp
 *
 */
public class ByteBufferOutputStream extends OutputStream {

	private ByteBuffer assignedBuffer = null;
	private Queue<ByteBuffer> overflows = new ArrayDeque<>();
	private ByteBuffer current = null;
	
	/**
	 * Creates a new instance.
	 */
	public ByteBufferOutputStream()	{
		super();
	}

	/**
	 * Assign a new buffer to this output stream. If the previously
	 * used buffer had become full and intermediate storage was allocated,
	 * the data from the intermediate storage is copied to the new buffer
	 * first. Then, the new buffer is used for all subsequent write
	 * operations.
	 * 
	 * @param buffer the buffer to use
	 */
	public void assignBuffer(ByteBuffer buffer) {
		assignedBuffer = buffer;
		// Move any overflow to the new buffer
		while (!overflows.isEmpty()) {
			ByteBuffer head = overflows.peek();
			// Do a "flip with position to mark"
			int writePos = head.position(); // Save position
			head.reset();
			head.limit(writePos);
			if (head.remaining() > assignedBuffer.remaining()) {
				// Cannot transfer everything, do what's possible
				head.limit(head.position() + assignedBuffer.remaining());
				assignedBuffer.put(head);
				// Advance mark and restore head for writing
				head.mark();
				head.limit(head.capacity());
				head.position(writePos);
				return;
			}
			assignedBuffer.put(head);
			overflows.remove();
		}
		current = assignedBuffer;
	}
	
	private void allocateOverflowBuffer() {
		current = ByteBuffer.allocate
				(assignedBuffer == null ? 4096 : assignedBuffer.capacity() / 4);
		current.mark();
		overflows.add(current);
	}
	
	/* (non-Javadoc)
	 * @see java.io.OutputStream#write(int)
	 */
	@Override
	public void write(int b) throws IOException {
		if (current == null || current.remaining() == 0) {
			allocateOverflowBuffer();
		}
		current.put((byte)b);
	}

	/* (non-Javadoc)
	 * @see java.io.OutputStream#write(byte[], int, int)
	 */
	@Override
	public void write(byte[] b, int offset, int length) throws IOException {
		if (current == null) {
			allocateOverflowBuffer();
		}
		while (true) {
			if (current.remaining() >= length) {
				current.put(b, offset, length);
				return;
			}
			if (current.remaining() > 0) {
				int processed = current.remaining();
				current.put(b, offset, processed);
				offset += processed;
				length -= processed;
			}
			allocateOverflowBuffer();
		}
	}

	/**
	 * Returns the number of bytes remaining in the assigned buffer.
	 * A negative value indicates that the assigned buffer is full
	 * and an overflow buffer is being used. 
	 * 
	 * @return the bytes remaining or -1
	 */
	public int remaining() {
		if (!overflows.isEmpty()) {
			return -1;
		}
		return assignedBuffer.remaining();
	}
}
