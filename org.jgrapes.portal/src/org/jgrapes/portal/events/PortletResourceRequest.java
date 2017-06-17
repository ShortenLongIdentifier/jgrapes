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

package org.jgrapes.portal.events;

import java.net.URI;

import org.jdrupes.httpcodec.protocols.http.HttpRequest;
import org.jgrapes.core.Event;
import org.jgrapes.io.IOSubchannel;

/**
 * 
 */
public class PortletResourceRequest extends Event<Boolean> {

	private HttpRequest httpRequest;
	private IOSubchannel httpChannel;
	private String portletId;
	private URI resourceUri;

	/**
	 * @param httpRequest
	 * @param portletId
	 * @param resourceUri
	 * @param channels
	 */
	public PortletResourceRequest(String portletId, URI resourceUri,
			HttpRequest httpRequest, IOSubchannel httpChannel) {
		this.portletId = portletId;
		this.resourceUri = resourceUri;
		this.httpRequest = httpRequest;
		this.httpChannel = httpChannel;
	}

	/**
	 * Returns the "raw" request as provided by the HTTP decoder.
	 * 
	 * @return the request
	 */
	public HttpRequest httpRequest() {
		return httpRequest;
	}

	/**
	 * @return the httpChannel
	 */
	public IOSubchannel httpChannel() {
		return httpChannel;
	}

	/**
	 * @return the portletId
	 */
	public String portletId() {
		return portletId;
	}

	/**
	 * @return the resourceUri
	 */
	public URI resourceUri() {
		return resourceUri;
	}
}
