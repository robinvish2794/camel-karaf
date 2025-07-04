package org.apache.camel.component.cxf.transport.jetty.osgi;

import org.apache.camel.component.cxf.blueprint.bus.BlueprintNameSpaceHandlerFactory;
import org.apache.camel.component.cxf.blueprint.bus.NamespaceHandlerRegisterer;
import org.apache.camel.component.cxf.transport.jetty.blueprint.HTTPJettyTransportNamespaceHandler;
import org.apache.cxf.common.util.CollectionUtils;
import org.apache.cxf.configuration.jsse.TLSParameterJaxBUtils;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.configuration.security.*;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngine;
import org.apache.cxf.transport.http_jetty.JettyHTTPServerEngineFactory;
import org.apache.cxf.transport.http_jetty.ThreadingParameters;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.tracker.ServiceTracker;

import javax.management.MBeanServer;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

public class HTTPJettyTransportActivator
        implements BundleActivator, ManagedServiceFactory {
    public static final String FACTORY_PID = "org.apache.cxf.http.jetty";

    BundleContext context;
    MBeanServer mbeans;
    ServiceTracker<MBeanServer, ?> mbeanServerTracker;
    ServiceRegistration<ManagedServiceFactory> reg;

    JettyHTTPServerEngineFactory factory = new JettyHTTPServerEngineFactory() {
        public MBeanServer getMBeanServer() {
            return (MBeanServer) mbeanServerTracker.getService();
        }
    };

    public void start(BundleContext ctx) throws Exception {
        this.context = ctx;
        mbeanServerTracker = new ServiceTracker<>(ctx, MBeanServer.class, null);

        BlueprintNameSpaceHandlerFactory factory = new BlueprintNameSpaceHandlerFactory() {

            @Override
            public Object createNamespaceHandler() {
                return new HTTPJettyTransportNamespaceHandler();
            }
        };

        NamespaceHandlerRegisterer.register(context, factory,
                "http://cxf.apache.org/transports/http-jetty/configuration");

        reg = context.registerService(ManagedServiceFactory.class,
                this,
                CollectionUtils.singletonDictionary(Constants.SERVICE_PID, FACTORY_PID));

    }

    public void stop(BundleContext ctx) throws Exception {
        mbeanServerTracker.close();
        reg.unregister();
    }

    public String getName() {
        return FACTORY_PID;
    }

    public void updated(String pid, Dictionary<String, ?> properties)
            throws ConfigurationException {
        if (pid == null) {
            return;
        }
        int port = Integer.parseInt((String) properties.get("port"));

        String host = (String) properties.get("host");
        try {
            TLSServerParameters tls = createTlsServerParameters(properties);
            if (tls != null) {
                factory.setTLSServerParametersForPort(host, port, tls);
            } else {
                factory.createJettyHTTPServerEngine(host, port, "http");
            }

            JettyHTTPServerEngine e = factory.retrieveJettyHTTPServerEngine(port);
            configure(e, properties);
        } catch (GeneralSecurityException | IOException e) {
            throw new ConfigurationException(null, null, e);
        }
    }


    private void configure(JettyHTTPServerEngine e, Dictionary<String, ?> properties) {
        ThreadingParameters threading = createThreadingParameters(properties);
        if (threading != null) {
            e.setThreadingParameters(threading);
        }
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String k = keys.nextElement();
            if ("sessionSupport".equals(k)) {
                e.setSessionSupport(Boolean.parseBoolean((String) properties.get(k)));
            } else if ("continuationsEnabled".equals(k)) {
                e.setContinuationsEnabled(Boolean.parseBoolean((String) properties.get(k)));
            } else if ("sendServerVersion".equals(k)) {
                e.setSendServerVersion(Boolean.parseBoolean((String) properties.get(k)));
            } else if ("reuseAddress".equals(k)) {
                e.setReuseAddress(Boolean.parseBoolean((String) properties.get(k)));
            } else if ("maxIdleTime".equals(k)) {
                e.setMaxIdleTime(Integer.parseInt((String) properties.get(k)));
            } else if ("sessionTimeout".equals(k)) {
                e.setSessionTimeout(Integer.parseInt((String) properties.get(k)));
            }
        }
    }

    public void deleted(String pid) {
    }

    private ThreadingParameters createThreadingParameters(Dictionary<String, ?> d) {
        Enumeration<String> keys = d.keys();
        ThreadingParameters p = null;
        while (keys.hasMoreElements()) {
            String k = keys.nextElement();
            if (k.startsWith("threadingParameters.")) {
                if (p == null) {
                    p = new ThreadingParameters();
                }
                String v = (String) d.get(k);
                k = k.substring("threadingParameters.".length());
                if ("minThreads".equals(k)) {
                    p.setMinThreads(Integer.parseInt(v));
                } else if ("maxThreads".equals(k)) {
                    p.setMaxThreads(Integer.parseInt(v));
                } else if ("threadNamePrefix".equals(k)) {
                    p.setThreadNamePrefix(k);
                }
            }
        }
        return p;
    }

    private TLSServerParameters createTlsServerParameters(Dictionary<String, ?> d) {
        Enumeration<String> keys = d.keys();
        TLSServerParameters p = null;
        SecureRandomParameters srp = null;
        KeyManagersType kmt = null;
        TrustManagersType tmt = null;
        boolean enableRevocation = false;
        while (keys.hasMoreElements()) {
            String k = keys.nextElement();
            if (k.startsWith("tlsServerParameters.")) {
                if (p == null) {
                    p = new TLSServerParameters();
                }
                String v = (String) d.get(k);
                k = k.substring("tlsServerParameters.".length());

                if ("secureSocketProtocol".equals(k)) {
                    p.setSecureSocketProtocol(v);
                } else if ("jsseProvider".equals(k)) {
                    p.setJsseProvider(v);
                } else if ("certAlias".equals(k)) {
                    p.setCertAlias(v);
                } else if ("clientAuthentication.want".equals(k)) {
                    if (p.getClientAuthentication() == null) {
                        p.setClientAuthentication(new ClientAuthentication());
                    }
                    p.getClientAuthentication().setWant(Boolean.parseBoolean(v));
                } else if ("clientAuthentication.required".equals(k)) {
                    if (p.getClientAuthentication() == null) {
                        p.setClientAuthentication(new ClientAuthentication());
                    }
                    p.getClientAuthentication().setRequired(Boolean.parseBoolean(v));
                } else if ("enableRevocation".equals(k)) {
                    enableRevocation = Boolean.parseBoolean(v);
                } else if (k.startsWith("certConstraints.")) {
                    configureCertConstraints(p, k, v);
                } else if (k.startsWith("secureRandomParameters.")) {
                    srp = configureSecureRandom(srp, k, v);
                } else if (k.startsWith("cipherSuitesFilter.")) {
                    configureCipherSuitesFilter(p, k, v);
                } else if (k.startsWith("cipherSuites")) {
                    StringTokenizer st = new StringTokenizer(v, ",");
                    while (st.hasMoreTokens()) {
                        p.getCipherSuites().add(st.nextToken());
                    }
                } else if (k.startsWith("excludeProtocols")) {
                    StringTokenizer st = new StringTokenizer(v, ",");
                    while (st.hasMoreTokens()) {
                        p.getExcludeProtocols().add(st.nextToken());
                    }
                } else if (k.startsWith("trustManagers.")) {
                    tmt = getTrustManagers(tmt,
                            k.substring("trustManagers.".length()),
                            v);
                } else if (k.startsWith("keyManagers.")) {
                    kmt = getKeyManagers(kmt,
                            k.substring("keyManagers.".length()),
                            v);
                }
            }
        }

        try {
            if (srp != null) {
                p.setSecureRandom(TLSParameterJaxBUtils.getSecureRandom(srp));
            }
            if (kmt != null) {
                p.setKeyManagers(TLSParameterJaxBUtils.getKeyManagers(kmt));
            }
            if (tmt != null) {
                p.setTrustManagers(TLSParameterJaxBUtils.getTrustManagers(tmt, enableRevocation));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return p;
    }

    private void configureCipherSuitesFilter(TLSServerParameters p, String k, String v) {
        k = k.substring("cipherSuitesFilter.".length());
        StringTokenizer st = new StringTokenizer(v, ",");
        FiltersType ft = p.getCipherSuitesFilter();
        if (ft == null) {
            ft = new FiltersType();
            p.setCipherSuitesFilter(ft);
        }
        List<String> lst = "include".equals(k) ? ft.getInclude() : ft.getExclude();
        while (st.hasMoreTokens()) {
            lst.add(st.nextToken());
        }
    }

    private SecureRandomParameters configureSecureRandom(SecureRandomParameters srp, String k, String v) {
        k = k.substring("secureRandomParameters.".length());
        if (srp == null) {
            srp = new SecureRandomParameters();
        }
        if ("algorithm".equals(k)) {
            srp.setAlgorithm(v);
        } else if ("provider".equals(k)) {
            srp.setProvider(v);
        }
        return srp;
    }

    private void configureCertConstraints(TLSServerParameters p, String k, String v) {
        k = k.substring("certConstraints.".length());
        CertificateConstraintsType cct = p.getCertConstraints();
        if (cct == null) {
            cct = new CertificateConstraintsType();
            p.setCertConstraints(cct);
        }
        DNConstraintsType dnct = null;
        if (k.startsWith("SubjectDNConstraints.")) {
            dnct = cct.getSubjectDNConstraints();
            if (dnct == null) {
                dnct = new DNConstraintsType();
                cct.setSubjectDNConstraints(dnct);
            }
            k = k.substring("SubjectDNConstraints.".length());
        } else if (k.startsWith("IssuerDNConstraints.")) {
            dnct = cct.getIssuerDNConstraints();
            if (dnct == null) {
                dnct = new DNConstraintsType();
                cct.setIssuerDNConstraints(dnct);
            }
            k = k.substring("IssuerDNConstraints.".length());
        }
        if (dnct != null) {
            if ("combinator".equals(k)) {
                dnct.setCombinator(CombinatorType.fromValue(v));
            } else if ("RegularExpression".equals(k)) {
                dnct.getRegularExpression().add(k);
            }
        }
    }

    private KeyManagersType getKeyManagers(KeyManagersType keyManagers, String k, String v) {
        if (keyManagers == null) {
            keyManagers = new KeyManagersType();
        }
        if ("factoryAlgorithm".equals(k)) {
            keyManagers.setFactoryAlgorithm(v);
        } else if ("provider".equals(k)) {
            keyManagers.setProvider(v);
        } else if ("keyPassword".equals(k)) {
            keyManagers.setKeyPassword(v);
        } else if (k.startsWith("keyStore.")) {
            keyManagers.setKeyStore(getKeyStore(keyManagers.getKeyStore(),
                    k.substring("keyStore.".length()),
                    v));
        }
        return keyManagers;
    }

    private KeyStoreType getKeyStore(KeyStoreType ks, String k, String v) {
        if (ks == null) {
            ks = new KeyStoreType();
        }
        if ("type".equals(k)) {
            ks.setType(v);
        } else if ("password".equals(k)) {
            ks.setPassword(v);
        } else if ("provider".equals(k)) {
            ks.setProvider(v);
        } else if ("url".equals(k)) {
            ks.setUrl(v);
        } else if ("file".equals(k)) {
            ks.setFile(v);
        } else if ("resource".equals(k)) {
            ks.setResource(v);
        }
        return ks;
    }

    private TrustManagersType getTrustManagers(TrustManagersType tmt, String k, String v) {
        if (tmt == null) {
            tmt = new TrustManagersType();
        }
        if ("provider".equals(k)) {
            tmt.setProvider(v);
        } else if ("factoryAlgorithm".equals(k)) {
            tmt.setFactoryAlgorithm(v);
        } else if (k.startsWith("keyStore.")) {
            tmt.setKeyStore(getKeyStore(tmt.getKeyStore(),
                    k.substring("keyStore.".length()),
                    v));
        } else if (k.startsWith("certStore")) {
            tmt.setCertStore(getCertStore(tmt.getCertStore(),
                    k.substring("certStore.".length()),
                    v));
        }
        return tmt;
    }

    private CertStoreType getCertStore(CertStoreType cs, String k, String v) {
        if (cs == null) {
            cs = new CertStoreType();
        }
        if ("file".equals(k)) {
            cs.setFile(v);
        } else if ("url".equals(k)) {
            cs.setUrl(v);
        } else if ("resource".equals(k)) {
            cs.setResource(v);
        }
        return cs;
    }


}
