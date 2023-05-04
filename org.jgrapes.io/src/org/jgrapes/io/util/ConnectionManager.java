/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2023 Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Affero General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public 
 * License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jgrapes.io.util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.Manager;
import org.jgrapes.core.Subchannel;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.annotation.HandlerDefinition.ChannelReplacements;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.events.Close;

/**
 * A base class for components that manage {@link Subchannel}s representing
 * some kind of connection to a server or service.
 *
 * @param <C> the type of the managed connections
 */
public abstract class ConnectionManager<
        C extends ConnectionManager<C>.Connection>
        extends Component {

    protected final Set<C> connections = new HashSet<>();
    private ExecutorService executorService;

    /**
     * Creates a new component base with its channel set to
     * itself.
     */
    public ConnectionManager() {
        super();
    }

    /**
     * Creates a new component base with its channel set to the given 
     * channel. As a special case {@link Channel#SELF} can be
     * passed to the constructor to make the component use itself
     * as channel. The special value is necessary as you 
     * obviously cannot pass an object to be constructed to its 
     * constructor.
     *
     * @param componentChannel the channel that the component's
     * handlers listen on by default and that 
     * {@link Manager#fire(Event, Channel...)} sends the event to
     */
    public ConnectionManager(Channel componentChannel,
            ChannelReplacements channelReplacements) {
        super(componentChannel, channelReplacements);
    }

    /**
     * Creates a new component base like {@link #Component(Channel)}
     * but with channel mappings for {@link Handler} annotations.
     *
     * @param componentChannel the channel that the component's
     * handlers listen on by default and that 
     * {@link Manager#fire(Event, Channel...)} sends the event to
     * @param channelReplacements the channel replacements to apply
     * to the `channels` elements of the {@link Handler} annotations
     */
    public ConnectionManager(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * If connections are event generators, register the component as
     * generator upon the creation of the first connection and unregister
     * it when closing the last one.  
     *
     * @return true, if connections generate
     */
    protected abstract boolean connectionsGenerate();

    /**
     * Sets an executor service to be used by the downstream event 
     * pipelines. Setting this to an executor service with a limited 
     * number of threads allows to control the maximum load caused 
     * by events generated by this component.
     * 
     * @param executorService the executorService to set
     * @return the connection manager for easy chaining
     * @see Manager#newEventPipeline(ExecutorService)
     */
    public ConnectionManager<C>
            setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * Returns the executor service.
     *
     * @return the executorService
     */
    public ExecutorService executorService() {
        if (executorService == null) {
            return Components.defaultExecutorService();
        }
        return executorService;
    }

    /**
     * Closes all connections.
     * 
     * @param event the event
     */
    @Handler
    public void onStop(Stop event) {
        while (true) {
            C connection;
            synchronized (connections) {
                var itr = connections.iterator();
                if (!itr.hasNext()) {
                    return;
                }
                connection = itr.next();
            }
            connection.close();
        }
    }

    /**
     * Closes the given connection.
     *
     * @param event the event
     * @param connection the connection
     */
    @Handler
    public void onClose(Close event, C connection) {
        synchronized (this) {
            if (connections.contains(connection)) {
                connection.close();
            }
        }
    }

    /**
     * The base class for the connections managed by this component.
     */
    public class Connection extends Subchannel.DefaultSubchannel {

        private final EventPipeline downPipeline;

        /**
         * @param mainChannel
         */
        @SuppressWarnings("unchecked")
        public Connection(Channel mainChannel) {
            super(mainChannel);
            synchronized (ConnectionManager.this) {
                if (connections.isEmpty() && connectionsGenerate()) {
                    registerAsGenerator();
                }
                connections.add((C) this);
            }
            if (executorService == null) {
                downPipeline = newEventPipeline();
            } else {
                downPipeline = newEventPipeline(executorService);
            }
        }

        /**
         * Gets the down pipeline.
         *
         * @return the downPipeline
         */
        public EventPipeline downPipeline() {
            return downPipeline;
        }

        /**
         * Closes the connection. If the last connection is closed
         * and the component is a generator (see 
         * {@link ConnectionManager#connectionsGenerate()), the component
         * is unregistered as generator.
         */
        public void close() {
            synchronized (this) {
                connections.remove(this);
                if (connections.isEmpty() && connectionsGenerate()) {
                    unregisterAsGenerator();
                }
            }
        }

    }
}
