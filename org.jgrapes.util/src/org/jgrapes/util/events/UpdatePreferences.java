/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2017  Michael N. Lipp
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

package org.jgrapes.util.events;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jgrapes.core.Event;

/**
 * 
 */
public class UpdatePreferences extends Event<Void> {

	private Map<String,Map<String,String>> scopes = new HashMap<>();
	
	/**
	 */
	public UpdatePreferences() {
	}

	public UpdatePreferences add(String scope, String key, String value) {
		if (scope == null) {
			scope = "";
		}
		Map<String,String> scoped = scopes
				.computeIfAbsent(scope, s -> new HashMap<String,String>());
		scoped.put(key, value);
		return this;
	}
	
	public Set<String> scopes() {
		return Collections.unmodifiableSet(scopes.keySet());
	}
	
	public Map<String,String> preferences(String scope) {
		return Collections.unmodifiableMap(
				scopes.getOrDefault(scope, Collections.emptyMap()));
	}
}
