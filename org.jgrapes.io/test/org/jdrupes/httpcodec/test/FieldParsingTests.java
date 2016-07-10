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
package org.jdrupes.httpcodec.test;

import static org.junit.Assert.*;

import java.text.ParseException;

import org.jdrupes.httpcodec.fields.HttpField;
import org.jdrupes.httpcodec.fields.HttpStringListField;
import org.jdrupes.httpcodec.fields.HttpMediaTypeField;
import org.jdrupes.httpcodec.fields.HttpStringField;
import org.junit.Test;

/**
 * @author Michael N. Lipp
 *
 */
public class FieldParsingTests {

	@Test
	public void testString() throws ParseException {
		HttpField<?> fv = HttpStringField.fromString("Test", "Hello");
		assertEquals("Hello", fv.getValue());
	}

	@Test
	public void testStringList() throws ParseException {
		HttpStringListField fv = HttpStringListField.fromString("Test",
		        "How, are,you,  out, there");
		assertEquals("How", fv.get(0));
		assertEquals("are", fv.get(1));
		assertEquals("you", fv.get(2));
		assertEquals("out", fv.get(3));
		assertEquals("there", fv.get(4));
		assertEquals(5, fv.size());
	}

	@Test
	public void testQuoted() throws ParseException {
		HttpStringListField fv = HttpStringListField.fromString("Test",
				"\"How \\\"are\",you,  \"out, there\"");
		assertEquals("How \"are", fv.get(0));
		assertEquals("you", fv.get(1));
		assertEquals("out, there", fv.get(2));
		assertEquals(3, fv.size());
	}

	@Test
	public void testUnquote() throws ParseException {
		HttpField<?> fv = HttpStringField.fromString("Test", "How are you?");
		assertEquals("How are you?", fv.getValue());
		fv = HttpStringField.fromString("Test", "\"How \\\"are\"");
		assertEquals("How \"are", fv.getValue());
	}
	
	@Test
	public void testMediaType() throws ParseException {
		HttpMediaTypeField mt = HttpMediaTypeField.fromString("Test",
		        "text/html;charset=utf-8");
		assertEquals("text/html; charset=utf-8", mt.valueToString());
		mt = HttpMediaTypeField.fromString("Test",
		        "Text/HTML;Charset=\"utf-8\"");
		assertEquals("text/html; charset=utf-8", mt.valueToString());
		mt = HttpMediaTypeField.fromString("Test",
		        "text/html; charset=\"utf-8\"");
		assertEquals("text/html; charset=utf-8", mt.valueToString());
	}
}
