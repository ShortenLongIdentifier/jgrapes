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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jgrapes.core.Channel;
import org.jgrapes.core.EventPipeline;

/**
 * This class represents the component tree. It holds all properties that 
 * are common to all nodes of a component tree (the {@link ComponentNode}s.
 * 
 * @author Michael N. Lipp
 */
class ComponentTree {

	private ComponentNode root;
	private Map<EventChannelsTuple,HandlerList> handlerCache
		= new HashMap<EventChannelsTuple,HandlerList>();
	private MergingEventPipeline eventPipeline;

	/**
	 * Creates a new tree for the given node or sub tree.
	 * 
	 * @param root the root node of the new tree
	 */
	ComponentTree(ComponentNode root) {
		super();
		this.root = root;
	}

	ComponentNode getRoot() {
		return root;
	}
	
	boolean isStarted() {
		return !(eventPipeline instanceof EventBuffer);
	}

	void setEventPipeline(MergingEventPipeline pipeline) {
		eventPipeline = pipeline;
	}
	
	/**
	 * Forward to the thread's event manager.
	 * 
	 * @param event
	 * @param channels
	 */
	public void fire(EventBase event, Channel[] channels) {
		eventPipeline.add(event, channels);
	}

	/**
	 * Merge all events stored with <code>source</code> with our own.
	 * This is invoked if a component (or component tree) is attached 
	 * to another tree (that uses this component common).
	 * 
	 * @param source
	 */
	void mergeEvents(ComponentTree source) {
		eventPipeline.merge(source.eventPipeline);
	}
	
	/**
	 * Send the event to all matching handlers.
	 * 
	 * @param event the event
	 * @param channels the channels the event is sent to
	 */
	void dispatch(EventPipeline pipeline, EventBase event, Channel[] channels) {
		HandlerList handlers = getEventHandlers(event, channels);
		handlers.process(pipeline, event);
	}
	
	private HandlerList getEventHandlers
		(EventBase event, Channel[] channels) {
		EventChannelsTuple key = new EventChannelsTuple(event, channels);
		HandlerList hdlrs = handlerCache.get(key);
		if (hdlrs != null) {
			return hdlrs;
		}
		hdlrs = new HandlerList();
		root.collectHandlers(hdlrs, event, channels);
		Collections.sort(hdlrs);
		handlerCache.put(key, hdlrs);
		return hdlrs;
	}
	
	void clearHandlerCache() {
		handlerCache.clear();
	}

}
