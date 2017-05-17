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

package org.jgrapes.core.internal;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jgrapes.core.ComponentType;

/**
 * Common utility methods.
 */
public class Common {

	private Common() {
	}

	private static AssertionError assertionError = null;
	
	static void setAssertionError(AssertionError error) {
		if (assertionError == null) {
			assertionError = error;
		}
	}

	public static void checkAssertions() {
		if (assertionError != null) {
			AssertionError error = assertionError;
			assertionError = null;
			throw error;
		}
	}
	
	public static final Logger classNames 
		= Logger.getLogger(ComponentType.class.getPackage().getName() 
			+ ".classNames");	

	public static String classToString(Class<?> clazz) {
		if (classNames.isLoggable(Level.FINER)) {
			return clazz.getName();
		} else {
			return clazz.getSimpleName();
		}
	}
	
}
