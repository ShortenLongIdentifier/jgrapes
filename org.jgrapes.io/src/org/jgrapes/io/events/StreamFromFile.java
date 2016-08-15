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

import java.nio.file.OpenOption;
import java.nio.file.Path;

import org.jgrapes.core.Event;

/**
 * Causes the content of a file to be streamed as sequence of {@link Output}
 * events, terminated by an {@link Eos} event.
 * 
 * @author Michael N. Lipp
 */
public class StreamFromFile extends Event<Void> {

	private Path path;
	private OpenOption[] options;

	/**
	 * @param path
	 * @param options
	 */
	public StreamFromFile(Path path, OpenOption... options) {
		this.path = path;
		this.options = options;
	}

	/**
	 * @return the path
	 */
	public Path getPath() {
		return path;
	}

	/**
	 * @return the options
	 */
	public OpenOption[] getOptions() {
		return options;
	}

}
