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
package org.jgrapes.io.test.http;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import org.jgrapes.core.Component;
import org.jgrapes.http.HttpServer;
import org.jgrapes.io.NioDispatcher;
import org.jgrapes.io.test.WaitForTests;
import org.jgrapes.net.Server;
import org.jgrapes.net.events.Ready;

/**
 * @author Michael N. Lipp
 *
 */
public class BasicTestServer extends Component {
	private InetSocketAddress addr;
	private WaitForTests readyMonitor;

	public BasicTestServer() throws IOException, InterruptedException, 
			ExecutionException {
		attach(new NioDispatcher());
		Server networkServer = attach(new Server(null));
		attach(new HttpServer(getChannel(), networkServer.getChannel()));
		readyMonitor = new WaitForTests
			(this, Ready.class, networkServer.getChannel().getMatchKey());
	}
	
	public InetSocketAddress getSocketAddress() 
				throws InterruptedException, ExecutionException {
		if (addr == null) {
			Ready readyEvent = (Ready) readyMonitor.get();
			if (!(readyEvent.getListenAddress() instanceof InetSocketAddress)) {
				fail();
			}
			addr = ((InetSocketAddress)readyEvent.getListenAddress());
		}
		return addr;
		
	}
	
	public InetAddress getAddress() 
				throws InterruptedException, ExecutionException {
		return getSocketAddress().getAddress();
	}
	
	public int getPort() 
				throws InterruptedException, ExecutionException {
		return getSocketAddress().getPort();
	}
}

