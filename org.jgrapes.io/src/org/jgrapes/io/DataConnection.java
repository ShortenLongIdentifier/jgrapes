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
package org.jgrapes.io;

import java.nio.ByteBuffer;

import org.jgrapes.core.EventPipeline;
import org.jgrapes.io.events.Write;
import org.jgrapes.io.util.ManagedByteBuffer;

/**
 * Represents an I/O connection that is used to transfer data.
 * 
 * @author Michael N. Lipp
 */
public interface DataConnection extends Connection {

	/**
	 * Get a {@link ByteBuffer} suitable to be passed to {@link Write} events.
	 * 
	 * @return the buffer
	 * @throws InterruptedException if the invoking thread is interrupted
	 * while waiting for a buffer
	 */
	ManagedByteBuffer acquireByteBuffer() throws InterruptedException;

	/**
	 * Get an {@link EventPipeline} that can be used to write events.
	 * Using the event pipeline associated with the connection
	 * ensures that the events are written in proper sequence.
	 */
	EventPipeline getPipeline();
	
}
