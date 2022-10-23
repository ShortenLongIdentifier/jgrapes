/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2022 Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Affero General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jgrapes.mail;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IdleManager;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Flags.Flag;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.event.ConnectionEvent;
import jakarta.mail.event.ConnectionListener;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Components.Timer;
import org.jgrapes.core.Event;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.events.Close;
import org.jgrapes.io.events.Closed;
import org.jgrapes.io.events.ConnectError;
import org.jgrapes.io.events.IOError;
import org.jgrapes.io.events.Opened;
import org.jgrapes.io.events.Opening;
import org.jgrapes.mail.events.MessagesRetrieved;
import org.jgrapes.mail.events.OpenMailMonitor;
import org.jgrapes.mail.events.ReceivedMessage;
import org.jgrapes.mail.events.RetrieveMessages;
import org.jgrapes.util.Password;

/**
 * A component that opens mail stores and monitors mail folders for 
 * mails. After establishing a connection to a store and selected 
 * folders (see {@link #onOpenMailMonitor(OpenMailMonitor, Channel)}), 
 * the existing and all subsequently arriving mails will be sent 
 * downstream using {@link ReceivedMessage} events.
 * 
 * This implementation uses the {@link IdleManager}. The 
 * {@link IdleManager} works only, if it is invoked (for a folder) after 
 * any operation on a folder. Note that operations such as e.g. setting 
 * the deleted flag of a message is also an operation on the folder.
 * 
 * Messages are retrieved from folders in response to a
 * {@link RetrieveMessages} event and delivered as result of this
 * event and individually by {@link ReceivedMessage} events. Folders
 * may be freely used while handling these messages, because the
 * folders will be re-registered with the {@link IdleManager}
 * when the {@link MessagesRetrieved} completion event is fired.
 * Any usage of folders independent of handling the events mentioned
 * will result in a loss of the monitor function.
 * 
 * The monitor function may be reestablished, however, by firing
 * a {@link RetrieveMessages} event for the folders used.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports" })
public class MailStoreMonitor
        extends MailConnectionManager<MailStoreMonitor.MonitorChannel> {

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Logger logger
        = Logger.getLogger(MailStoreMonitor.class.getName());

    private Duration maxIdleTime = Duration.ofMinutes(25);
    private static IdleManager idleManager;
    private ExecutorService executorService;

    /**
     * Creates a new server using the given channel.
     * 
     * @param componentChannel the component's channel
     */
    public MailStoreMonitor(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * Sets an executor service to be used by the event pipelines
     * that process the data from the network. Setting this
     * to an executor service with a limited number of threads
     * allows to control the maximum load from the network.
     * 
     * @param executorService the executorService to set
     * @return the TCP connection manager for easy chaining
     * @see Manager#newEventPipeline(ExecutorService)
     */
    public MailStoreMonitor
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
        return executorService;
    }

    /**
     * Sets the maximum idle time. A running {@link IMAPFolder#idle()}
     * is terminated and renewed after this time. Defaults to 25 minutes.
     *
     * @param maxIdleTime the new max idle time
     */
    public MailStoreMonitor setMaxIdleTime(Duration maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
        return this;
    }

    /**
     * Returns the max idle time.
     *
     * @return the duration
     */
    public Duration maxIdleTime() {
        return maxIdleTime;
    }

    /**
     * Configure the component. Currently, only max idle time
     * is supported.
     *
     * @param values the values
     */
    @Override
    protected void configureComponent(Map<String, String> values) {
        Optional.ofNullable(values.get("maxIdleTime"))
            .map(Integer::parseInt).map(Duration::ofSeconds)
            .ifPresent(d -> setMaxIdleTime(d));
    }

    /**
     * Open a store as specified by the event and monitor the folders
     * (also specified by the event). All existing and all subsequently 
     * arriving mails will be sent downstream using 
     * {@link ReceivedMessage} events.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onOpenMailMonitor(OpenMailMonitor event, Channel channel) {
        Properties sessionProps = new Properties(mailProps);
        sessionProps.putAll(event.mailProperties());
        sessionProps.put("mail.imap.usesocketchannels", true);
        Session session = Session.getInstance(sessionProps);
//            // Workaround for class loading problem in OSGi with j.m. 2.1.
//            // Authenticator's classpath allows accessing provider's service.
//            // See https://github.com/eclipse-ee4j/mail/issues/631
//            new Authenticator() {
//                @Override
//                protected PasswordAuthentication
//                        getPasswordAuthentication() {
//                    return new PasswordAuthentication(
//                        sessionProps.getProperty("mail.user"),
//                        new String(event.password().password()));
//                }
//            });

        try {
            synchronized (MailStoreMonitor.class) {
                // Cannot be created earlier, need session.
                if (idleManager == null) {
                    idleManager = new IdleManager(session,
                        Components.defaultExecutorService());
                }
            }
            new MonitorChannel(event, channel, session.getStore(),
                sessionProps.getProperty("mail.user"), event.password());
        } catch (NoSuchProviderException e) {
            fire(new ConnectError(event, "Cannot create store.", e));
        } catch (IOException e) {
            fire(new IOError(event, "Cannot create resource.", e));
        }
    }

    /**
     * Closes the channel.
     *
     * @param event the event
     */
    @Handler
    public void onClose(Close event, Channel channel) {
        if (channels.contains(channel)) {
            ((MonitorChannel) channel).close();
        }
    }

    /**
     * Retrieves new messages from the folders specified in the event.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onRetrieveMessages(RetrieveMessages event,
            MailChannel channel) {
        if (!channels.contains(channel)) {
            return;
        }
        ((MonitorChannel) channel).retrieveMessages(event);
    }

    /**
     * Registers the folders from which messages have been received
     * with the {@link IdleManager}.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler(priority = -1000)
    public void onMessagesRetrieved(MessagesRetrieved event,
            MailChannel channel) {
        if (!channels.contains(channel)) {
            return;
        }
        ((MonitorChannel) channel).messagesRetrieved(event);
    }

    /**
     * Stops the thread that is associated with this dispatcher.
     * 
     * @param event the event
     * @throws InterruptedException if the execution is interrupted
     */
    @Handler
    public void onStop(Stop event) throws InterruptedException {
        while (true) {
            MonitorChannel channel;
            synchronized (channels) {
                var itr = channels.iterator();
                if (!itr.hasNext()) {
                    return;
                }
                channel = itr.next();
            }
            channel.close();
        }
    }

    /**
     * The Enum ChannelState.
     */
    @SuppressWarnings("PMD.FieldNamingConventions")
    private enum ChannelState {
        Opening {
            @Override
            public boolean isOpening() {
                return true;
            }
        },
        Open {
            @Override
            public boolean isOpen() {
                return true;
            }
        },
        Reopening {
            @Override
            public boolean isOpening() {
                return true;
            }
        },
        Reopened {
            @Override
            public boolean isOpen() {
                return true;
            }
        },
        Closing,
        Closed;

        /**
         * Checks if is open.
         *
         * @return true, if is open
         */
        public boolean isOpen() {
            return false;
        }

        /**
         * Checks if is opening.
         *
         * @return true, if is opening
         */
        public boolean isOpening() {
            return false;
        }
    }

    /**
     * The specific implementation of the {@link MailChannel}.
     */
    protected class MonitorChannel extends MailConnectionManager<
            MailStoreMonitor.MonitorChannel>.AbstractMailChannel
            implements ConnectionListener {

        private final EventPipeline requestPipeline;
        private ChannelState state = ChannelState.Opening;
        private final Store store;
        private final String user;
        private final Password password;
        private final String[] folderNames;
        private final Map<String, Folder> folders;
        private final Set<Message> messages;
        private final Timer idleTimer;

        /**
         * Instantiates a new monitor channel.
         *
         * @param event the event
         * @param channel the channel
         * @param store the store
         * @param password 
         * @param string 
         */
        public MonitorChannel(OpenMailMonitor event, Channel channel,
                Store store, String user, Password password) {
            super(event, channel);
            this.store = store;
            this.user = user;
            this.password = password;
            this.folderNames = event.folderNames();
            requestPipeline = event.processedBy().get();
            folders = new ConcurrentHashMap<>();
            messages = new HashSet<>();
            store.addConnectionListener(this);
            idleTimer = Components.schedule(t -> {
                requestPipeline.fire(new RetrieveMessages(folderNames), this);
            }, maxIdleTime);
            connect(
                t -> downPipeline().fire(new ConnectError(event, t), channel));
        }

        /**
         * Attempt connections until connected. Attempts are stopped
         * if it is the first time that the connection is to be
         * established and the error indicates that the connection
         * will never succeed (e.g. due to an authentication
         * problem).
         *
         * @param onOpenFailed the on open failed
         */
        private void connect(Consumer<Throwable> onOpenFailed) {
            synchronized (this) {
                if (state.isOpen()) {
                    return;
                }
                activeEventPipeline().executorService().submit(() -> {
                    while (state.isOpening()) {
                        try {
                            attemptConnect(onOpenFailed);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });
            }
        }

        /**
         * Single connection attempt.
         *
         * @param onOpenFailed the on open failed
         * @throws InterruptedException the interrupted exception
         */
        @SuppressWarnings("PMD.AvoidInstanceofChecksInCatchClause")
        private void attemptConnect(Consumer<Throwable> onOpenFailed)
                throws InterruptedException {
            try {
                store.connect(user, new String(password.password()));
                synchronized (this) {
                    if (state == ChannelState.Opening) {
                        state = ChannelState.Open;
                    } else {
                        state = ChannelState.Reopened;
                        // Already registered
                        return;
                    }
                }
                // Works "in general", register.
                synchronized (channels) {
                    if (channels.isEmpty()) {
                        registerAsGenerator();
                    }
                    channels.add(this);
                }
            } catch (MessagingException e) {
                synchronized (this) {
                    if (state == ChannelState.Opening
                        && (e instanceof AuthenticationFailedException
                            || e instanceof NoSuchProviderException)) {
                        logger.log(Level.WARNING,
                            "Connecting to store failed, closing.", e);
                        state = ChannelState.Closed;
                        if (onOpenFailed != null) {
                            onOpenFailed.accept(e);
                        }
                        return;
                    }
                }
                logger.log(Level.WARNING,
                    "(Re)connecting to store failed, retrying.", e);
                Thread.sleep(5000);
            }
        }

        /**
         * Close the connection to the store.
         */
        public void close() {
            synchronized (this) {
                if (state == ChannelState.Closing
                    || state == ChannelState.Closed) {
                    return;
                }
                state = ChannelState.Closing;
            }

            idleTimer.cancel();
            try {
                // Initiate close, callback will inform downstream components.
                store.close();
            } catch (MessagingException e) {
                // According to the documentation, the listeners should
                // be invoked nevertheless.
                logger.log(Level.WARNING, "Cannot close connection properly.",
                    e);
            }
        }

        /**
         * Callback from store.connect is the connection is successful.
         *
         * @param event the event
         */
        @Override
        @SuppressWarnings({ "PMD.GuardLogStatement",
            "PMD.AvoidDuplicateLiterals" })
        public void opened(ConnectionEvent event) {
            if (state == ChannelState.Reopened) {
                // This is a re-open, only retrieve messages.
                requestPipeline.fire(new RetrieveMessages(folderNames), this);
                return;
            }
            // (1) Opening, (2) Opened, (3) start retrieving mails
            downPipeline().fire(Event.onCompletion(new Opening<Void>(),
                o -> downPipeline().fire(
                    Event.onCompletion(new Opened<Store>().setResult(store),
                        p -> requestPipeline
                            .fire(new RetrieveMessages(folderNames), this)),
                    this)),
                this);
        }

        /**
         * According to the documentation,
         * {@link ConnectionEvent#DISCONNECTED} is currently not
         * used. It's implemented nevertheless and called explicitly.
         *
         * @param event the event or `null` if called explicitly
         */
        @Override
        public void disconnected(ConnectionEvent event) {
            messages.clear();
            folders.clear();
            synchronized (this) {
                if (state.isOpen()) {
                    state = ChannelState.Reopening;
                    connect(null);
                }
            }
        }

        /**
         * Callback that indicates the connection close,
         * can be called any time by jakarta mail.
         * 
         * Whether closing is intended (callback after a call to 
         * {@link #close}) can be checked by looking at the state. 
         *
         * @param event the event
         */
        @Override
        public void closed(ConnectionEvent event) {
            // Ignore if already closed.
            if (state == ChannelState.Closed) {
                return;
            }

            // Handle involuntary close by reopening.
            if (state != ChannelState.Closing) {
                disconnected(event);
                return;
            }

            // Cleanup and remove channel.
            messages.clear();
            folders.clear();
            downPipeline().fire(new Closed());
            synchronized (channels) {
                channels.remove(this);
                if (channels.isEmpty()) {
                    unregisterAsGenerator();
                }
            }
        }

        /**
         * Retrieve the new messages from the folders specified in the
         * event.
         * 
         * @param event
         */
        @SuppressWarnings("PMD.CognitiveComplexity")
        public void retrieveMessages(RetrieveMessages event) {
            @SuppressWarnings("PMD.UseConcurrentHashMap")
            Map<Folder, List<Message>> result = new HashMap<>();
            if (store.isConnected()) {
                try {
                    for (var folderName : event.folderNames()) {
                        Folder folder = folders.get(folderName);
                        if (folder == null) {
                            folder = folderFromStore(folderName);
                            if (folder == null) {
                                continue;
                            }
                            folders.put(folderName, folder);
                        }
                        var newMsgs = scanFolder(folder);
                        if (!newMsgs.isEmpty()) {
                            result.put(folder, newMsgs);
                        }
                    }
                } catch (FolderClosedException e) {
                    disconnected(null);
                }
            } else {
                disconnected(null);
            }
            event.setResult(result);
        }

        @SuppressWarnings({ "PMD.GuardLogStatement",
            "PMD.AvoidRethrowingException" })
        private Folder folderFromStore(String folderName)
                throws FolderClosedException {
            try {
                Folder folder = store.getFolder(folderName);
                if (folder == null || !folder.exists()) {
                    logger.fine(() -> "No folder \"" + folderName
                        + "\" in store " + store);
                    return null;
                }
                folder.open(Folder.READ_WRITE);
                // Add MessageCountListener to listen for new messages.
                folder.addMessageCountListener(new MessageCountAdapter() {
                    @Override
                    public void messagesAdded(MessageCountEvent countEvent) {
                        requestPipeline.fire(new RetrieveMessages(folderName),
                            MonitorChannel.this);
                    }
                });
                return folder;
            } catch (FolderClosedException e) {
                throw e;
            } catch (MessagingException e) {
                logger.log(Level.FINE,
                    "Cannot open folder: " + e.getMessage(), e);
            }
            return null;
        }

        @SuppressWarnings({ "PMD.GuardLogStatement",
            "PMD.AvoidRethrowingException" })
        private List<Message> scanFolder(Folder folder)
                throws FolderClosedException {
            List<Message> newMsgs = new ArrayList<>();
            try {
                Set<Message> found = new HashSet<>(messages);
                for (var msg : folder.getMessages()) {
                    found.add(msg);
                    if (!messages.contains(msg)) {
                        // New message.
                        newMsgs.add(msg);
                        processMessage(msg);
                    }
                }
                messages.retainAll(found);
            } catch (FolderClosedException e) {
                throw e;
            } catch (MessagingException e) {
                logger.log(Level.WARNING,
                    "Problem processing messages: " + e.getMessage(), e);
            }
            return newMsgs;
        }

        @SuppressWarnings({ "PMD.GuardLogStatement",
            "PMD.AvoidRethrowingException" })
        private void processMessage(Message msg) throws FolderClosedException {
            try {
                if (msg.getFlags().contains(Flag.DELETED)) {
                    return;
                }
                downPipeline().fire(new ReceivedMessage(msg), this).get();
            } catch (FolderClosedException e) {
                throw e;
            } catch (MessagingException e) {
                logger.log(Level.WARNING,
                    "Problem processing message: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                return;
            }
        }

        /**
        * Registers the folders from which messages have been received
        * with the {@link IdleManager}.
         *
         * @param event the event
         */
        public void messagesRetrieved(MessagesRetrieved event) {
            for (String folderName : event.event().folderNames()) {
                Folder folder = folders.get(folderName);
                if (folder == null) {
                    continue;
                }
                try {
                    idleManager.watch(folder);
                } catch (MessagingException e) {
                    logger.log(Level.WARNING, "Cannot watch folder.",
                        e);
                }
            }
            idleTimer.reschedule(maxIdleTime);
        }
    }

    @Override
    public String toString() {
        return Components.objectName(this);
    }

}
