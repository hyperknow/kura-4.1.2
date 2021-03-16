/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Red Hat Inc
 *  Eurotech
 *
 *******************************************************************************/
package org.eclipse.kura.jetty.customizer;

import java.util.Dictionary;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.DispatcherType;

import org.eclipse.equinox.http.jetty.JettyCustomizer;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration.Customizer;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import net.bull.javamelody.Parameter;

public class KuraJettyCustomizer extends JettyCustomizer {

	public Server serverWithMelody(Server server) {
		final Map<Parameter, String> parameters = new HashMap<>();
		// to add basic auth:
		// parameters.put(Parameter.AUTHORIZED_USERS, "admin:pwd");

		// to change the default storage directory:
		// parameters.put(Parameter.STORAGE_DIRECTORY, "/tmp/javamelody");

		// to change the default resolution in seconds:
		// parameters.put(Parameter.RESOLUTION_SECONDS, "60");

		// to hide all statistics such as http and sql, except logs:
		// parameters.put(Parameter.DISPLAYED_COUNTERS, "log");
		// parameters.put(Parameter.NO_DATABASE, "true");

		// enable hotspots sampling with a period of 1 second:
		parameters.put(Parameter.SAMPLING_SECONDS, "1.0");

		// set the path of the reports:
		parameters.put(Parameter.MONITORING_PATH, "/");

		final ContextHandlerCollection contexts = new ContextHandlerCollection();
		final ServletContextHandler context = new ServletContextHandler(contexts, "/", ServletContextHandler.SESSIONS);

		final net.bull.javamelody.MonitoringFilter monitoringFilter = new net.bull.javamelody.MonitoringFilter();
		monitoringFilter.setApplicationType("Standalone");
		final FilterHolder filterHolder = new FilterHolder(monitoringFilter);
		if (parameters != null) {
			for (final Map.Entry<Parameter, String> entry : parameters.entrySet()) {
				final net.bull.javamelody.Parameter parameter = entry.getKey();
				final String value = entry.getValue();
				filterHolder.setInitParameter(parameter.getCode(), value);
			}
		}
		context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.INCLUDE, DispatcherType.REQUEST));

		final RequestLogHandler requestLogHandler = new RequestLogHandler();
		contexts.addHandler(requestLogHandler);

		final HandlerCollection handlers = new HandlerCollection();
		handlers.setHandlers(new Handler[] { contexts });
		server.setHandler(handlers);

		return server;
	}

	@Override
	public Object customizeHttpConnector(final Object connector, final Dictionary<String, ?> settings) {
		customizeConnector(connector);
		return connector;
	}

	@Override
	public Object customizeHttpsConnector(final Object connector, final Dictionary<String, ?> settings) {
		customizeConnector(connector);
		return connector;
	}

	private void customizeConnector(Object connector) {
		if (!(connector instanceof ServerConnector)) {
			return;
		}

		final ServerConnector serverConnector = (ServerConnector) connector;
		serverWithMelody(serverConnector.getServer());

		for (final ConnectionFactory factory : serverConnector.getConnectionFactories()) {
			if (!(factory instanceof HttpConnectionFactory)) {
				continue;
			}

			final HttpConnectionFactory httpConnectionFactory = (HttpConnectionFactory) factory;

			httpConnectionFactory.getHttpConfiguration().setSendServerVersion(false);

			List<Customizer> customizers = httpConnectionFactory.getHttpConfiguration().getCustomizers();
			if (customizers == null) {
				customizers = new LinkedList<>();
				httpConnectionFactory.getHttpConfiguration().setCustomizers(customizers);
			}

			customizers.add(new ForwardedRequestCustomizer());
		}
	}

}
