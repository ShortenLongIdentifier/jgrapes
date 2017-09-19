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

package org.jgrapes.http.demo.portlets.helloworld;

import org.jdrupes.json.JsonBeanDecoder;
import org.jdrupes.json.JsonBeanEncoder;
import org.jdrupes.json.JsonDecodeException;
import org.jgrapes.core.Channel;
import org.jgrapes.core.CompletionLock;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.http.Session;
import org.jgrapes.io.IOSubchannel;
import org.jgrapes.portal.PortalView;
import org.jgrapes.portal.events.AddPortletRequest;
import org.jgrapes.portal.events.AddPortletType;
import org.jgrapes.portal.events.DeletePortlet;
import org.jgrapes.portal.events.DeletePortletRequest;
import org.jgrapes.portal.events.NotifyPortletModel;
import org.jgrapes.portal.events.NotifyPortletView;
import org.jgrapes.portal.events.PortalReady;
import org.jgrapes.portal.events.RenderPortletFromProvider;
import org.jgrapes.portal.events.RenderPortletRequest;
import org.jgrapes.portal.freemarker.FreeMarkerPortlet;
import org.jgrapes.util.events.KeyValueStoreData;
import org.jgrapes.util.events.KeyValueStoreQuery;
import org.jgrapes.util.events.KeyValueStoreUpdate;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;

import static org.jgrapes.portal.Portlet.*;
import static org.jgrapes.portal.Portlet.RenderMode.*;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * 
 */
public class HelloWorldPortlet extends FreeMarkerPortlet {

	private final static Set<RenderMode> MODES = RenderMode.asSet(
			DeleteablePreview, View);
	
	/**
	 * Creates a new component with its channel set to the given 
	 * channel.
	 * 
	 * @param componentChannel the channel that the component's 
	 * handlers listen on by default and that 
	 * {@link Manager#fire(Event, Channel...)} sends the event to 
	 */
	public HelloWorldPortlet(Channel componentChannel) {
		super(componentChannel);
	}

	private String storagePath(Session session) {
		return "/" + session.getOrDefault(Principal.class, "").toString()
			+ "/portlets/" + HelloWorldPortlet.class.getName()	+ "/";
	}
	
	@Handler
	public void onPortalReady(PortalReady event, IOSubchannel channel) 
			throws TemplateNotFoundException, MalformedTemplateNameException, 
			ParseException, IOException {
		ResourceBundle resourceBundle = resourceBundle(locale(channel));
		// Add HelloWorldPortlet resources to page
		channel.respond(new AddPortletType(type())
				.setDisplayName(resourceBundle.getString("portletName"))
				.addScript(PortalView.uriFromPath("HelloWorld-functions.js"))
				.addCss(PortalView.uriFromPath("HelloWorld-style.css"))
				.setInstantiable());
		Optional<Session> optSession = channel.associated(Session.class);
		if (optSession.isPresent()) {
			KeyValueStoreQuery query = new KeyValueStoreQuery(
					storagePath(optSession.get()), true);
			channel.setAssociated(
					HelloWorldPortlet.class, new CompletionLock(event, 3000));
			fire(query, channel);
		}
	}

	@Handler
	public void onKeyValueStoreData(
			KeyValueStoreData event, IOSubchannel channel) 
					throws JsonDecodeException {
		Optional<Session> optSession = channel.associated(Session.class);
		if (!optSession.isPresent() ||
				!event.event().query().equals(storagePath(optSession.get()))) {
			return;
		}
		channel.associated(HelloWorldPortlet.class, CompletionLock.class)
			.ifPresent(lock -> lock.remove());
		Session session = session(channel);
		for (String json: event.data().values()) {
			HelloWorldModel model = JsonBeanDecoder.create(json)
					.readObject(HelloWorldModel.class);
			addToSession(session, model);
		}
	}
	
	public void doAddPortlet(AddPortletRequest event,
			IOSubchannel channel, Session session,
			PortletBaseModel portletModel) throws Exception {
		String jsonState = JsonBeanEncoder.create()
				.writeObject(portletModel).toJson();
		channel.respond(new KeyValueStoreUpdate().update(storagePath(session) 
				+ portletModel.getPortletId(), jsonState));
		Template tpl = freemarkerConfig().getTemplate("HelloWorld-preview.ftlh");
		Map<String, Object> baseModel 
			= freemarkerBaseModel(event.renderSupport());
		channel.respond(new RenderPortletFromProvider(
				HelloWorldPortlet.class, portletModel.getPortletId(),
				DeleteablePreview, MODES, newContentProvider(tpl, 
						freemarkerModel(baseModel, portletModel, channel)),
				true));
	}

	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#doDeletePortlet(org.jgrapes.portal.events.DeletePortletRequest, org.jgrapes.io.IOSubchannel, org.jgrapes.portal.AbstractPortlet.PortletModelBean)
	 */
	@Override
	protected void doDeletePortlet(DeletePortletRequest event,
	        IOSubchannel channel, Session session, PortletBaseModel portletModel)
	        		throws Exception {
		channel.respond(new KeyValueStoreUpdate().delete(
				storagePath(session) + portletModel.getPortletId()));
		channel.respond(new DeletePortlet(portletModel.getPortletId()));
	}
	
	
	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#doRenderPortlet(org.jgrapes.portal.events.RenderPortletRequest, org.jgrapes.io.IOSubchannel, org.jgrapes.portal.AbstractPortlet.PortletModelBean)
	 */
	@Override
	protected void doRenderPortlet(RenderPortletRequest event,
	        IOSubchannel channel, Session session, PortletBaseModel retrievedModel)
	        throws Exception {
		HelloWorldModel portletModel = (HelloWorldModel)retrievedModel;
		Map<String, Object> baseModel = freemarkerBaseModel(event.renderSupport());
		switch (event.renderMode()) {
		case Preview:
		case DeleteablePreview: {
			Template tpl = freemarkerConfig().getTemplate("HelloWorld-preview.ftlh");
			channel.respond(new RenderPortletFromProvider(
					HelloWorldPortlet.class, portletModel.getPortletId(), 
					DeleteablePreview, MODES,	newContentProvider(tpl, 
							freemarkerModel(baseModel, portletModel, channel)),
					event.isForeground()));
			break;
		}
		case View: {
			Template tpl = freemarkerConfig().getTemplate("HelloWorld-view.ftlh");
			channel.respond(new RenderPortletFromProvider(
					HelloWorldPortlet.class, portletModel.getPortletId(), 
					View, MODES, newContentProvider(tpl, 
							freemarkerModel(baseModel, portletModel, channel)),
					event.isForeground()));
			channel.respond(new NotifyPortletView(type(),
					portletModel.getPortletId(), "setWorldVisible",
					portletModel.isWorldVisible()));
			break;
		}
		default:
			break;
		}	
	}
	
	@Handler
	public void onChangePortletModel(NotifyPortletModel event,
			IOSubchannel channel) throws TemplateNotFoundException, 
			MalformedTemplateNameException, ParseException, IOException {
		Session session = session(channel);
		Optional<? extends PortletBaseModel> optPortletModel 
			= modelFromSession(session, event.portletId());
		if (!optPortletModel.isPresent()) {
			return;
		}
	
		event.stop();
		HelloWorldModel portletModel = (HelloWorldModel)optPortletModel.get();
		portletModel.setWorldVisible(!portletModel.isWorldVisible());
		
		String jsonState = JsonBeanEncoder.create()
				.writeObject(portletModel).toJson();
		channel.respond(new KeyValueStoreUpdate().update(storagePath(session) 
				+ portletModel.getPortletId(), jsonState));
		channel.respond(new NotifyPortletView(type(),
				portletModel.getPortletId(), "setWorldVisible", 
				portletModel.isWorldVisible()));
	}
	
	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#createModelBean()
	 */
	@Override
	protected PortletBaseModel createPortletModel() {
		return new HelloWorldModel(generatePortletId());
	}

	@SuppressWarnings("serial")
	public static class HelloWorldModel extends PortletBaseModel {

		private boolean worldVisible = true;
		
		/**
		 * Creates a new model with the given type and id.
		 * 
		 * @param portletId the portlet id
		 */
		@ConstructorProperties({"portletId"})
		public HelloWorldModel(String portletId) {
			super(portletId);
		}

		/**
		 * @param visible the visible to set
		 */
		public void setWorldVisible(boolean visible) {
			this.worldVisible = visible;
		}

		public boolean isWorldVisible() {
			return worldVisible;
		}
	}
	
}
