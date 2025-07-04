/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.camel.component.cxf.transport.jetty.blueprint;

import org.apache.aries.blueprint.Namespaces;
import org.apache.aries.blueprint.ParserContext;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.camel.component.cxf.blueprint.helpers.BaseNamespaceHandler;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.net.URL;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Namespaces("http://cxf.apache.org/transports/http-jetty/configuration")
public class HTTPJettyTransportNamespaceHandler extends BaseNamespaceHandler {

    public static final String JETTY_TRANSPORT = "http://cxf.apache.org/transports/http-jetty/configuration";

    private static final String JETTY_ENGINE = "engine";

    private static final String JETTY_ENGINE_FACTORY = "engine-factory";

    private static final Logger LOG = LogUtils.getL7dLogger(HTTPJettyTransportNamespaceHandler.class);

    public URL getSchemaLocation(String s) {
        if (JETTY_TRANSPORT.equals(s)) {
            return getClass().getClassLoader().
                getResource("schemas/configuration/http-jetty.xsd");
        }
        return super.findCoreSchemaLocation(s);
    }

    @SuppressWarnings("rawtypes")
    public Set<Class> getManagedClasses() {
        return null;
    }

    public Metadata parse(Element element, ParserContext parserContext) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Parsing element {{" + element.getNamespaceURI() + "}}{" + element.getLocalName() + "}");
        }

        if (JETTY_ENGINE.equals(element.getLocalName())) {
            //This doesn't hit normal configs.
            return new JettyServerEngineParser().parse(element, parserContext);
        } else if (JETTY_ENGINE_FACTORY.equals(element.getLocalName())) {

            return new JettyServerEngineFactoryParser().parse(element, parserContext);
        }

        return null;
    }

    public ComponentMetadata decorate(Node node,
                                      ComponentMetadata componentMetadata,
                                      ParserContext parserContext) {
        LOG.info("Decorating node " + node + " " + componentMetadata);
        return componentMetadata;
    }
}
