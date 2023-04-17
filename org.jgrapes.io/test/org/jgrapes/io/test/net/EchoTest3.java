/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2016-2018 Michael N. Lipp
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

package org.jgrapes.io.test.net;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.HandlingError;
import org.jgrapes.core.events.Started;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.IOSubchannel;
import org.jgrapes.io.NioDispatcher;
import org.jgrapes.io.events.Close;
import org.jgrapes.io.events.Closed;
import org.jgrapes.io.events.Input;
import org.jgrapes.io.events.OpenTcpConnection;
import org.jgrapes.io.events.Output;
import org.jgrapes.io.test.WaitForTests;
import org.jgrapes.io.util.ByteBufferOutputStream;
import org.jgrapes.io.util.ManagedBuffer;
import org.jgrapes.net.SslCodec;
import org.jgrapes.net.TcpConnector;
import org.jgrapes.net.TcpServer;
import org.jgrapes.net.events.ClientConnected;
import org.jgrapes.net.events.Ready;
import static org.junit.Assert.*;
import org.junit.Test;

public class EchoTest3 {

    public class EchoServer extends Component {

        /**
         * @throws IOException 
         */
        public EchoServer() throws IOException {
        }

        @Handler
        public void onInput(Input<ByteBuffer> event, IOSubchannel channel)
                throws InterruptedException {

//            ByteBuffer buf = event.data().duplicate();
//            byte[] data = new byte[buf.remaining()];
//            buf.get(data);
//            int range = Math.min(20, data.length);
//            String start = new String(data, 0, range);
//            String tail
//                = new String(data, data.length - range, range);
//            System.out.println("Server received: "
//                + start.replace("\n", "\\n") + " ... "
//                + tail.replace("\n", "\\n"));

            ManagedBuffer<ByteBuffer> out = channel.byteBufferPool().acquire();
            out.backingBuffer().put(event.data());
            channel.respond(Output.fromSink(out, event.isEndOfRecord()));
        }
    }

    public class Done extends Event<Void> {
    }

    public class ClientApp extends Component {
        private InetSocketAddress serverAddr;
        private ByteArrayOutputStream responseBuffer;
        private boolean handlingErrorSeen;
        public boolean infoPropagated;

        /**
         * @param serverAddr
         */
        public ClientApp(InetSocketAddress serverAddr) {
            this.serverAddr = serverAddr;
        }

        @Handler
        public void onStarted(Started event) throws InterruptedException {
            fire(new OpenTcpConnection(serverAddr).setAssociated("test", true));
        }

        @Handler
        public void onConnected(ClientConnected event, IOSubchannel channel)
                throws InterruptedException, IOException {
            infoPropagated
                = event.openEvent().associated("test", Boolean.class).get();
            channel.setAssociated(EchoTest3.class, this);
            new Thread(() -> {
                try (Writer out = new OutputStreamWriter(
                    new ByteBufferOutputStream(channel).suppressClose())) {
                    for (int i = 0; i < 100000; i++) {
                        out.write(String.format("%9d\n", i));
                    }
                    out.write("\032");
                } catch (IOException e) {
                    fail();
                }
            }).start();
        }

        @Handler
        public void onInput(Input<ByteBuffer> event, IOSubchannel channel)
                throws IOException {
            if (!(channel.associated(EchoTest3.class, Object.class)
                .map(o -> o == this).orElse(false))) {
                return;
            }
            if (responseBuffer == null) {
                responseBuffer = new ByteArrayOutputStream();
            }

//            ByteBuffer buf = event.data().duplicate();
//            byte[] data = new byte[buf.remaining()];
//            buf.get(data);
//            int range = Math.min(20, data.length);
//            String start = new String(data, 0, range);
//            String tail
//                = new String(data, data.length - range, range);
//            System.out.println("Client received: "
//                + start.replace("\n", "\\n") + " ... "
//                + tail.replace("\n", "\\n"));

            ByteBuffer check = event.data().duplicate();
            check.position(check.remaining() - 1);
            if (check.get() != '\032') {
                Channels.newChannel(responseBuffer).write(event.data());
                return;
            }
            event.data().limit(event.data().limit() - 1);
            Channels.newChannel(responseBuffer).write(event.data());
            channel.respond(new Close());
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(
                    responseBuffer.toByteArray())))) {
                int lastNum = -1;
                while (true) {
                    String line = in.readLine();
                    if (line == null) {
                        break;
                    }
                    int num = Integer.parseInt(line.trim());
                    assertEquals(lastNum + 1, num);
                    lastNum = num;
                }
                assertEquals(100000 - 1, lastNum);
            }
            responseBuffer = null;
        }

        @Handler
        public void onClosed(Closed<?> event, IOSubchannel channel)
                throws IOException {
            fire(new Done());
        }

        @Handler
        public void onHandlingError(HandlingError event) {
            if (!handlingErrorSeen) {
                handlingErrorSeen = true;
                throw new AssertionError(event.message(), event.throwable());
            }
        }
    }

    @Test
    public void testTcp() throws IOException, InterruptedException,
            ExecutionException, TimeoutException {
        // Create server
        EchoServer srvApp = new EchoServer();
        srvApp.attach(new TcpServer(srvApp));
        srvApp.attach(new NioDispatcher());
        WaitForTests<Ready> wf = new WaitForTests<>(
            srvApp, Ready.class, srvApp.defaultCriterion());
        Components.start(srvApp);
        Ready readyEvent = (Ready) wf.get();
        if (!(readyEvent.listenAddress() instanceof InetSocketAddress)) {
            fail();
        }
        InetSocketAddress serverAddr = new InetSocketAddress("localhost",
            ((InetSocketAddress) readyEvent.listenAddress()).getPort());

        // Create client
        ClientApp clntApp = new ClientApp(serverAddr);
        clntApp.attach(new TcpConnector(clntApp));
        clntApp.attach(new NioDispatcher());
        WaitForTests<Done> done
            = new WaitForTests<>(clntApp, Done.class,
                clntApp.defaultCriterion());
        Components.start(clntApp);
        done.get();
        assertTrue(clntApp.infoPropagated);

        // Stop
        Components.manager(clntApp).fire(new Stop(), Channel.BROADCAST);
        Components.manager(srvApp).fire(new Stop(), Channel.BROADCAST);
        long waitEnd = System.currentTimeMillis() + 3000;
        while (true) {
            long waitTime = waitEnd - System.currentTimeMillis();
            if (waitTime <= 0) {
                fail();
            }
            Components.checkAssertions();
            try {
                assertTrue(Components.awaitExhaustion(waitTime));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            break;
        }
        Components.checkAssertions();
    }

    @Test
    public void testSsl() throws IOException, InterruptedException,
            ExecutionException, TimeoutException, KeyStoreException,
            NoSuchAlgorithmException, CertificateException,
            UnrecoverableKeyException, KeyManagementException {
        // Create server
        EchoServer srvApp = new EchoServer();
        srvApp.attach(new NioDispatcher());

        // Create TLS "converter"
        KeyStore serverStore = KeyStore.getInstance("JKS");
        try (FileInputStream kf
            = new FileInputStream("test-resources/localhost.jks")) {
            serverStore.load(kf, "nopass".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverStore, "nopass".toCharArray());
        SSLContext sslSrvContext = SSLContext.getInstance("TLS");
        sslSrvContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        // Create a TCP server for SSL
        TcpServer secSrvNetwork = srvApp.attach(new TcpServer());
        srvApp.attach(new SslCodec(srvApp, secSrvNetwork, sslSrvContext));

        // Server prepared, start it.
        WaitForTests<Ready> wf = new WaitForTests<>(
            secSrvNetwork, Ready.class, secSrvNetwork.defaultCriterion());
        Components.start(srvApp);
        Ready readyEvent = (Ready) wf.get();
        if (!(readyEvent.listenAddress() instanceof InetSocketAddress)) {
            fail();
        }
        InetSocketAddress serverAddr = new InetSocketAddress("localhost",
            ((InetSocketAddress) readyEvent.listenAddress()).getPort());

        // Create client
        ClientApp clntApp = new ClientApp(serverAddr);
        clntApp.attach(new NioDispatcher());

        // Create a TCP connector for SSL
        TcpConnector secClntNetwork = clntApp.attach(new TcpConnector());
        clntApp.attach(new SslCodec(clntApp, secClntNetwork, true));
        WaitForTests<Ready> done
            = new WaitForTests<>(clntApp, Done.class,
                clntApp.defaultCriterion());
        Components.start(clntApp);
        done.get();

        // Stop
        Components.manager(clntApp).fire(new Stop(), Channel.BROADCAST);
        Components.manager(srvApp).fire(new Stop(), Channel.BROADCAST);
        long waitEnd = System.currentTimeMillis() + 300000;
        while (true) {
            long waitTime = waitEnd - System.currentTimeMillis();
            if (waitTime <= 0) {
                fail();
            }
            Components.checkAssertions();
            try {
                assertTrue(Components.awaitExhaustion(waitTime));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            break;
        }
        Components.checkAssertions();
    }

}
