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
package org.jgrapes.http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;

import org.jdrupes.httpcodec.protocols.http.HttpResponse;
import org.jdrupes.httpcodec.protocols.http.fields.HttpField;
import org.jdrupes.httpcodec.protocols.http.fields.HttpMediaTypeField;
import org.jdrupes.httpcodec.protocols.http.HttpConstants.HttpStatus;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.http.annotation.RequestHandler;
import org.jgrapes.http.events.GetRequest;
import org.jgrapes.http.events.Response;
import org.jgrapes.io.IOSubchannel;
import org.jgrapes.io.events.StreamFile;

/**
 * @author Michael N. Lipp
 */
public class StaticContentDispatcher extends Component {

	private Path prefix;
	private Path contentDirectory;
	
	/**
	 * @param prefix the prefix that requests must start with to be handled
	 * by this component
	 * @param contentDirectory the directory with content to serve 
	 * @see Component#Component()
	 */
	public StaticContentDispatcher(Path prefix, Path contentDirectory) {
		this(Channel.SELF, prefix, contentDirectory);
	}

	/**
	 * @param componentChannel
	 *            this component's channel
	 * @param prefix the prefix that requests must start with to be handled
	 * by this component
	 * @param contentDirectory the directory with content to serve 
	 * @see Component#Component(Channel)
	 */
	public StaticContentDispatcher(Channel componentChannel, Path prefix,
	        Path contentDirectory) {
		super(componentChannel);
		this.prefix = prefix;
		this.contentDirectory = contentDirectory;
		RequestHandler.Evaluator.add(this, "onGet", prefix.toString());
	}

	@RequestHandler(dynamic=true)
	public void onGet(GetRequest event) throws ParseException, IOException {
		Path requestPath = Paths
		        .get(event.getRequest().getRequestUri().getPath());
		if (!requestPath.startsWith(prefix)) {
			return;
		}
		Path resourcePath = contentDirectory
		        .resolve(prefix.relativize(requestPath));
		if (Files.isDirectory(resourcePath)) {
			Path indexPath = resourcePath.resolve("index.html");
			if (Files.isReadable(indexPath)) {
				resourcePath = indexPath;
			} else {
				return;
			}
		}
		if (!Files.isReadable(resourcePath)) {
			return;
		}
		IOSubchannel channel = event.firstChannel(IOSubchannel.class);
		String mimeTypeName = Files.probeContentType(resourcePath);
		if (mimeTypeName == null) {
			mimeTypeName = "application/octet-stream";
		}
		HttpMediaTypeField contentType = HttpMediaTypeField
		        .fromString(HttpField.CONTENT_TYPE, mimeTypeName);
		if (contentType.getBaseType().equals("text")) {
			contentType.setParameter("charset",
			        System.getProperty("file.encoding", "UTF-8"));
		}
		final HttpResponse response = event.getRequest().getResponse().get();
		response.setStatus(HttpStatus.OK);
		response.setMessageHasBody(true);
		response.setField(contentType);
		channel.fire (new Response(response));
		channel.fire(new StreamFile(resourcePath, StandardOpenOption.READ));
		event.stop();
	}
}
