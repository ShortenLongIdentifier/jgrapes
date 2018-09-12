/*
 * JGrapes Event driven Framework
 * Copyright (C) 2018 Michael N. Lipp
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jgrapes.io.events;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.jgrapes.core.Event;

/**
 * 
 */
public class OpenTcpConnection extends Event<Void> {

    private final InetSocketAddress address;

    /**
     * Signals that a new TCP connection should be opened.
     *
     * @param address the address
     */
    public OpenTcpConnection(InetSocketAddress address) {
        this.address = address;
    }

    /**
     * Signals that a new TCP connection should be opened.
     *
     * @param address the address
     * @param port the port
     */
    public OpenTcpConnection(InetAddress address, int port) {
        this.address = new InetSocketAddress(address, port);
    }

    /**
     * Gets the address.
     *
     * @return the address
     */
    public InetSocketAddress address() {
        return address;
    }
}
