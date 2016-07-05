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
package org.jgrapes.io.events;

import org.jgrapes.io.DataConnection;
import org.jgrapes.io.util.ManagedBuffer;
import org.jgrapes.io.util.ManagedCharBuffer;

/**
 * This event signals that a new chunk of data is to be forwarded to the
 * destination. The data is kept in a buffer.
 * 
 * @author Michael N. Lipp
 */
public class Write<T extends ManagedBuffer<?>> 
	extends ConnectionEvent<Void, DataConnection> {

	private T buffer;
	
	/**
	 * Create a new event with the given buffer that must have been
	 * obtained from the connection's 
	 * {@link DataConnection#acquireWriteBuffer()}. Creating the event
	 * flips the buffer as it is assumed to be used for reading by
	 * the handlers(s) from now on.
	 * 
	 * @param connection the connection to write the data to
	 * @param buffer the buffer with the data
	 */
	public Write(DataConnection connection, T buffer) {
		super(connection);
		this.buffer = buffer;
		buffer.flip();
	}

	/**
	 * Convenience method that wraps a String in a 
	 * {@code Write<ManagedCharBuffer} event.
	 * 
	 * @param connection the connection to write the data to
	 * @param data the string to wrap
	 * @return the event
	 */
	public static Write<ManagedCharBuffer> 
			wrap(DataConnection connection, String data) {
		return new Write<>(connection, new ManagedCharBuffer(data));
	}
	
	/**
	 * Get the buffer with the data from this event.
	 * 
	 * @return the buffer
	 */
	public T getBuffer() {
		return buffer;
	}

	/**
	 * Releases the buffer, unless locked.
	 */
	@Override
	synchronized protected void handled() {
		buffer.unlockBuffer();
	}
	
}
