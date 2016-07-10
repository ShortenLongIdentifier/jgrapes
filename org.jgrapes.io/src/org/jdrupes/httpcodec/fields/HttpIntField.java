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
package org.jdrupes.httpcodec.fields;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;

/**
 * An HTTP field value that is an integer.
 * 
 * @author Michael N. Lipp
 */
public class HttpIntField extends HttpField<Long> {

	private long value;
	
	/**
	 * Creates the object with the given value.
	 * 
	 * @param name the field name
	 * @param value the field value
	 * @throws ParseException 
	 */
	public HttpIntField(String name, long value) {
		super(name);
		this.value = value;
	}

	protected static <T extends HttpIntField> T fromString
		(Class<T> type, String name, String s) throws ParseException {
		try {
			T result = type.getConstructor(String.class, Long.class)
			        .newInstance(name, 0);
			try {
				((HttpIntField)result).value = Long.parseLong(unquote(s));
			} catch (NumberFormatException e) {
				throw new ParseException(s, 0);
			}
			return result;
		} catch (InstantiationException | IllegalAccessException
		        | IllegalArgumentException | InvocationTargetException
		        | NoSuchMethodException | SecurityException e) {
			throw new IllegalArgumentException();
		}
	}
	
	/**
	 * Creates a new object with a value obtained by parsing the given
	 * String.
	 * 
	 * @param name the field name
	 * @param s the string to parse
	 * @throws ParseException 
	 */
	public static HttpIntField fromString(String name, String s)
			throws ParseException {
		return fromString(HttpIntField.class, name, s);
	}

	/**
	 * Returns the value.
	 * 
	 * @return the value
	 */
	@Override
	public Long getValue() {
		return value;
	}
	
	/**
	 * Returns the value.
	 * 
	 * @return the value
	 */
	public int asInt() {
		return (int)value;
	}

	/* (non-Javadoc)
	 * @see org.jdrupes.httpcodec.util.HttpFieldValue#asString()
	 */
	@Override
	public String valueToString() {
		return Long.toString(value);
	}
	
	
}
