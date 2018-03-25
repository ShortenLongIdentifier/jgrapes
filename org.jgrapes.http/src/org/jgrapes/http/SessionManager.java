/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2017-2018 Michael N. Lipp
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

import java.lang.management.ManagementFactory;
import java.net.HttpCookie;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.jdrupes.httpcodec.protocols.http.HttpField;
import org.jdrupes.httpcodec.protocols.http.HttpRequest;
import org.jdrupes.httpcodec.protocols.http.HttpResponse;
import org.jdrupes.httpcodec.types.CacheControlDirectives;
import org.jdrupes.httpcodec.types.Converters;
import org.jdrupes.httpcodec.types.CookieList;
import org.jdrupes.httpcodec.types.Directive;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.internal.EventBase;
import org.jgrapes.http.annotation.RequestHandler;
import org.jgrapes.http.events.DiscardSession;
import org.jgrapes.http.events.Request;
import org.jgrapes.http.events.WebSocketAccepted;
import org.jgrapes.io.IOSubchannel;

/**
 * A base class for session managers. A session manager associates 
 * {@link Request} events with a {@link Session} object using 
 * `Session.class` as association identifier. The {@link Request}
 * handler has a default priority of 1000.
 * 
 * Managers track requests using a cookie with a given name and path. The 
 * path is a prefix that has to be matched by the request, often "/".
 * If no cookie with the given name (see {@link #idName()}) is found,
 * a new cookie with that name and the specified path is created.
 * The cookie's value is the unique session id that is used to lookup
 * the session object.
 * 
 * Session managers provide additional support for web sockets. If a
 * web socket is accepted, the session associated with the request
 * is automatically associated with the {@link IOSubchannel} that
 * is subsequently used for the web socket events. This allows
 * handlers for web socket messages to access the session like
 * {@link Request} handlers.
 * 
 * @see EventBase#setAssociated(Object, Object)
 * @see "[OWASP Session Management Cheat Sheet](https://www.owasp.org/index.php/Session_Management_Cheat_Sheet)"
 */
public abstract class SessionManager extends Component {
	
	private static SecureRandom secureRandom = new SecureRandom();

	private String idName = "id";
	private String path = "/";
	private long absoluteTimeout = 9*60*60*1000;
	private long idleTimeout = 30*60*1000;
	private int maxSessions = 1000;
	
	/**
	 * Creates a new session manager with its channel set to
	 * itself and the path set to "/". The manager handles
	 * all {@link Request} events.
	 */
	public SessionManager() {
		this("/");
	}
	
	/**
	 * Creates a new session manager with its channel set to
	 * itself and the path set to the given path. The manager
	 * handles all requests that match the given path, using the
	 * same rules as browsers do for selecting the cookies that
	 * are to be sent.
	 * 
	 * @param path the path
	 */
	public SessionManager(String path) {
		this(Channel.SELF, path);
	}

	/**
	 * Creates a new session manager with its channel set to
	 * the given channel and the path to "/". The manager handles
	 * all {@link Request} events.
	 * 
	 * @param componentChannel the component channel
	 */
	public SessionManager(Channel componentChannel) {
		this(componentChannel, "/");
	}

	/**
	 * Creates a new session manager with the given channel and path.
	 * The manager handles all requests that match the given path, using
	 * the same rules as browsers do for selecting the cookies that
	 * are to be sent.
	 *  
	 * @param componentChannel the component channel
	 * @param path the path
	 */
	public SessionManager(Channel componentChannel, String path) {
		this(componentChannel, derivePattern(path), 1000, path);
	}

	/**
	 * Derives the resource pattern from the path.
	 *
	 * @param path the path
	 * @return the pattern
	 */
	protected static String derivePattern(String path) {
		String pattern;
		if (path.equals("/")) {
			pattern = "/**";
		} else {
			String patternBase = path;
			if (patternBase.endsWith("/")) {
				patternBase = path.substring(0, path.length() - 1);
			}
			pattern = path + "," + path + "/**";
		}
		return pattern;
	}
	
	/**
	 * Creates a new session manager using the given channel and path.
	 * The manager handles only requests that match the given pattern.
	 * The handler is registered with the given priority.
	 * 
	 * This constructor can be used if special handling of top level
	 * requests is needed.
	 *
	 * @param componentChannel the component channel
	 * @param pattern the path part of a {@link ResourcePattern}
	 * @param priority the priority
	 * @param path the path
	 */
	public SessionManager(Channel componentChannel, String pattern, 
			int priority, String path) {
		super(componentChannel);
		this.path = path;
		RequestHandler.Evaluator.add(this, "onRequest", pattern, priority);
		MBeanView.addManager(this);
	}

	/**
	 * The name used for the session id cookie. Defaults to "`id`".
	 * 
	 * @return the id name
	 */
	public String idName() {
		return idName;
	}
	
	/**
	 * @param idName the id name to set
	 * 
	 * @return the session manager for easy chaining
	 */
	public SessionManager setIdName(String idName) {
		this.idName = idName;
		return this;
	}

	/**
	 * Set the maximum number of sessions. If the value is zero or less,
	 * an unlimited number of sessions is supported. The default value
	 * is 1000.
	 * 
	 * If adding a new session would exceed the limit, first all
	 * sessions older than {@link #absoluteTimeout()} are removed.
	 * If this doesn't free a slot, the least recently used session
	 * is removed.
	 * 
	 * @param maxSessions the maxSessions to set
	 * @return the session manager for easy chaining
	 */
	public SessionManager setMaxSessions(int maxSessions) {
		this.maxSessions = maxSessions;
		return this;
	}

	/**
	 * @return the maxSessions
	 */
	public int maxSessions() {
		return maxSessions;
	}

	/**
	 * Sets the absolute timeout for a session in seconds. The absolute
	 * timeout is the time after which a session is invalidated (relative
	 * to its creation time). Defaults to 9 hours. Zero or less disables
	 * the timeout.
	 * 
	 * @param absoluteTimeout the absolute timeout
	 * @return the session manager for easy chaining
	 */
	public SessionManager setAbsoluteTimeout(int absoluteTimeout) {
		this.absoluteTimeout = absoluteTimeout * 1000;
		return this;
	}

	/**
	 * @return the absolute session timeout (in seconds)
	 */
	public int absoluteTimeout() {
		return (int)(absoluteTimeout / 1000);
	}

	/**
	 * Sets the idle timeout for a session in seconds. Defaults to 30 minutes.
	 * Zero or less disables the timeout. 
	 * 
	 * @param idleTimeout the absolute timeout
	 * @return the session manager for easy chaining
	 */
	public SessionManager setIdleTimeout(int idleTimeout) {
		this.idleTimeout = idleTimeout * 1000;
		return this;
	}

	/**
	 * @return the idle timeout (in seconds)
	 */
	public int idleTimeout() {
		return (int)(idleTimeout / 1000);
	}

	/**
	 * Associates the event with a {@link Session} object
	 * using `Session.class` as association identifier.
	 * 
	 * @param event the event
	 */
	@RequestHandler(dynamic=true)
	public void onRequest(Request event) {
		if (event.associated(Session.class).isPresent()) {
			return;
		}
		final HttpRequest request = event.httpRequest();
		Optional<String> requestedSessionId = request.findValue(
		        HttpField.COOKIE, Converters.COOKIE_LIST)
		        .flatMap(cookies -> cookies.stream().filter(
		                cookie -> cookie.getName().equals(idName()))
		                .findFirst().map(HttpCookie::getValue));
		if (requestedSessionId.isPresent()) {
			String sessionId = requestedSessionId.get();
			synchronized(SessionManager.this) {
				Optional<Session> session = lookupSession(sessionId);
				if (session.isPresent()) {
					Instant now = Instant.now();
					if ((absoluteTimeout <= 0
							|| Duration.between(session.get().createdAt(), 
									now).toMillis() < absoluteTimeout)
						&& (idleTimeout <= 0
							|| Duration.between(session.get().lastUsedAt(),
									now).toMillis() < idleTimeout)) {
						event.setAssociated(Session.class, session.get());
						session.get().updateLastUsedAt();
						return;
					}
					// Invalidate, too old 
					removeSession(sessionId);
				}
			}
		}
		String sessionId = createSessionId(request.response().get());
		Session session = createSession(sessionId);
		event.setAssociated(Session.class, session);
	}

	/**
	 * Creates a new session with the given id.
	 * 
	 * @param sessionId
	 * @return the session
	 */
	protected abstract Session createSession(String sessionId);

	/**
	 * Lookup the session with the given id.
	 * 
	 * @param sessionId
	 * @return the session
	 */
	protected abstract Optional<Session> lookupSession(String sessionId);

	/**
	 * Removed the given session.
	 * 
	 * @param sessionId the session id
	 */
	protected abstract void removeSession(String sessionId);

	/**
	 * Return the number of established sessions.
	 * 
	 * @return the result
	 */
	protected abstract int sessionCount();
	
	/**
	 * Creates a session id and adds the corresponding cookie to the
	 * response.
	 * 
	 * @param response the response
	 * @return the session id
	 */
	protected String createSessionId(HttpResponse response) {
		StringBuilder sessionIdBuilder = new StringBuilder();
		byte[] bytes = new byte[16];
		secureRandom.nextBytes(bytes);
		for (byte b: bytes) {
			sessionIdBuilder.append(Integer.toHexString(b & 0xff));
		}
		String sessionId = sessionIdBuilder.toString();
		HttpCookie sessionCookie = new HttpCookie(idName(), sessionId);
		sessionCookie.setPath(path);
		sessionCookie.setHttpOnly(true);
		response.computeIfAbsent(HttpField.SET_COOKIE, CookieList::new)
			.value().add(sessionCookie);
		response.computeIfAbsent(
				HttpField.CACHE_CONTROL, CacheControlDirectives::new)
			.value().add(new Directive("no-cache", "SetCookie, Set-Cookie2"));
		return sessionId;
	}
	
	/**
	 * Discards the given session.
	 * 
	 * @param event the event
	 */
	@Handler(channels=Channel.class)
	public void discard(DiscardSession event) {
		removeSession(event.session().id());
	}

	/**
	 * Associates the channel with the session from the upgrade request.
	 * 
	 * @param event the event
	 * @param channel the channel
	 */
	@Handler(priority=1000)
	public void onWebSocketAccepted(
			WebSocketAccepted event, IOSubchannel channel) {
		event.requestEvent().associated(Session.class)
			.ifPresent(session -> {
				channel.setAssociated(Session.class, session);
			});
	}
	
	/**
	 * An MBean interface for getting information about the 
	 * established sessions.
	 */
	public static interface SessionManagerMXBean {
		
		public static class SessionManagerInfo {

			private SessionManager sessionManager;
			
			public SessionManagerInfo(SessionManager sessionManager) {
				this.sessionManager = sessionManager;
			}
			
			public int getMaxSessions() {
				return sessionManager.maxSessions();
			}
			
			public int getAbsoluteTimeout() {
				return sessionManager.absoluteTimeout();
			}
			
			public int getIdleTimeout() {
				return sessionManager.idleTimeout();
			}
			
			public int getSessionCount() {
				return sessionManager.sessionCount();
			}
		}
		
		SortedMap<String,SessionManagerInfo> getManagers();
	}
	
	private static class MBeanView implements SessionManagerMXBean {
		private static Set<SessionManager> allManagers
			= Collections.synchronizedSet(
					Collections.newSetFromMap(
							new WeakHashMap<SessionManager, Boolean>()));
		
		public static void addManager(SessionManager manager) {
			allManagers.add(manager);
		}
		
		@Override
		public SortedMap<String,SessionManagerInfo> getManagers() {
			SortedMap<String,SessionManagerInfo> result = new TreeMap<>();
			for (SessionManager manager: allManagers) {
				result.put(Components.objectName(manager) 
						+ " (" + manager.idName() + "," + manager.path + ")",
						new SessionManagerInfo(manager));
			}
			return result;
		}
	}

	static {
		try {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer(); 
			ObjectName mxbeanName = new ObjectName("org.jgrapes.http:type="
					+ SessionManager.class.getSimpleName());
			mbs.registerMBean(new MBeanView(), mxbeanName);
		} catch (MalformedObjectNameException | InstanceAlreadyExistsException
				| MBeanRegistrationException | NotCompliantMBeanException e) {
			// Does not happen
		}		
	}
}
