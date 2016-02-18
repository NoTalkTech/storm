/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.storm.ui;

import clojure.lang.Keyword;
import clojure.lang.RT;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.storm.generated.ExecutorInfo;
import org.apache.storm.logging.filters.AccessLoggingFilter;
import org.apache.storm.utils.Utils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.simple.JSONValue;

import javax.servlet.Servlet;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.*;

public class UIHelpers {

    private static final String[][] PRETTY_SEC_DIVIDERS = {
            new String[]{"s", "60"},
            new String[]{"m", "60"},
            new String[]{"h", "24"},
            new String[]{"d", null}};

    private static final String[][] PRETTY_MS_DIVIDERS = {
            new String[]{"ms", "1000"},
            new String[]{"s", "60"},
            new String[]{"m", "60"},
            new String[]{"h", "24"},
            new String[]{"d", null}};

    public static String prettyUptimeStr(String val, String[][] dividers) {
        int uptime = Integer.parseInt(val);
        LinkedList<String> tmp = new LinkedList<>();
        for (String[] divider : dividers) {
            if (uptime > 0) {
                if (divider[1] != null) {
                    int div = Integer.parseInt(divider[1]);
                    tmp.addFirst(uptime % div + divider[0]);
                    uptime = uptime / div;
                } else {
                    tmp.addFirst(uptime + divider[0]);
                }
            }
        }
        return Joiner.on(" ").join(tmp);
    }

    public static String prettyUptimeSec(String sec) {
        return prettyUptimeStr(sec, PRETTY_SEC_DIVIDERS);
    }

    public static String prettyUptimeSec(int secs) {
        return prettyUptimeStr(String.valueOf(secs), PRETTY_SEC_DIVIDERS);
    }

    public static String prettyUptimeMs(String ms) {
        return prettyUptimeStr(ms, PRETTY_MS_DIVIDERS);
    }

    public static String prettyUptimeMs(int ms) {
        return prettyUptimeStr(String.valueOf(ms), PRETTY_MS_DIVIDERS);
    }


    public static String urlFormat(String fmt, Object... args) {
        String[] argsEncoded = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            argsEncoded[i] = URLEncoder.encode(String.valueOf(args[i]));
        }
        return String.format(fmt, argsEncoded);
    }

    public static String prettyExecutorInfo(ExecutorInfo e) {
        return "[" + e.get_task_start() + "-" + e.get_task_end() + "]";
    }

    public static Map unauthorizedUserJson(String user) {
        return ImmutableMap.of(
                "error", "No Authorization",
                "errorMessage", String.format("User %s is not authorized.", user)
        );
    }

    public static List unauthorizedUserHtml(String user) {
        return Lists.newArrayList(
                keyword("h1"),
                "User '",
                StringEscapeUtils.escapeHtml(user),
                "' is not authorized.");
    }

    private static SslSocketConnector mkSslConnector(Integer port, String ksPath, String ksPassword, String ksType,
                                                     String keyPassword, String tsPath, String tsPassword, String tsType,
                                                     Boolean needClientAuth, Boolean wantClientAuth) {
        SslContextFactory factory = new SslContextFactory();
        factory.setExcludeCipherSuites("SSL_RSA_WITH_RC4_128_MD5", "SSL_RSA_WITH_RC4_128_SHA");
        factory.setExcludeProtocols("SSLv3");
        factory.setAllowRenegotiate(false);
        factory.setKeyStorePath(ksPath);
        factory.setKeyStoreType(ksType);
        factory.setKeyStorePassword(ksPassword);
        factory.setKeyManagerPassword(keyPassword);

        if (tsPath != null && tsPassword != null && tsType != null) {
            factory.setTrustStore(tsPath);
            factory.setTrustStoreType(tsType);
            factory.setTrustStorePassword(tsPassword);
        }

        if (needClientAuth != null && needClientAuth) {
            factory.setNeedClientAuth(true);
        }
        if (wantClientAuth != null && wantClientAuth) {
            factory.setWantClientAuth(true);
        }

        SslSocketConnector sslConnector = new SslSocketConnector(factory);
        sslConnector.setPort(port);
        return sslConnector;
    }

    public static void configSsl(Server server, Integer port, String ksPath, String ksPassword, String ksType,
                                 String keyPassword, String tsPath, String tsPassword, String tsType, Boolean needClientAuth, Boolean wantClientAuth) {
        if (port > 0) {
            server.addConnector(mkSslConnector(port, ksPath, ksPassword, ksType, keyPassword,
                    tsPath, tsPassword, tsType, needClientAuth, wantClientAuth));
        }
    }

    public static FilterHolder corsFilterHandle() {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "GET, POST, PUT");
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "X-Requested-With, X-Requested-By, Access-Control-Allow-Origin, Content-Type, Content-Length, Accept, Origin");
        filterHolder.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        return filterHolder;
    }

    public static FilterHolder mkAccessLoggingFilterHandle() {
        return new FilterHolder(new AccessLoggingFilter());
    }

    public static void configFilter(Server server, Servlet servlet, List filtersConfs) {
        if (filtersConfs != null) {
            ServletHolder servletHolder = new ServletHolder(servlet);
            ServletContextHandler context = new ServletContextHandler(server, "/");
            context.addServlet(servletHolder, "/");
            context.addFilter(corsFilterHandle(), "/*", EnumSet.allOf(DispatcherType.class));
            for (Object obj : filtersConfs) {
                Map filterConf = (Map) obj;
                String filterName = (String) filterConf.get(keyword("filter-name"));
                String filterClass = (String) filterConf.get(keyword("filter-class"));
                Map filterParams = (Map) filterConf.get(keyword("filter-params"));
                if (filterClass != null) {
                    FilterHolder filterHolder = new FilterHolder();
                    filterHolder.setClassName(filterClass);
                    if (filterName != null) {
                        filterHolder.setName(filterName);
                    } else {
                        filterHolder.setName(filterClass);
                    }
                    if (filterParams != null) {
                        filterHolder.setInitParameters(filterParams);
                    } else {
                        filterHolder.setInitParameters(new HashMap<String, String>());
                    }
                    context.addFilter(filterHolder, "/*", FilterMapping.ALL);
                }
            }
            context.addFilter(mkAccessLoggingFilterHandle(), "/*", EnumSet.allOf(DispatcherType.class));
            server.setHandler(context);
        }
    }

    public static Map ringResponseFromException(Exception ex) {
        return ImmutableMap.of(
                keyword("headers"), new HashMap<>(),
                keyword("status"), 400,
                keyword("body"), ex.getMessage()
        );
    }

    private static Server removeNonSslConnector(Server server) {
        for (Connector c : server.getConnectors()) {
            if (c != null && !(c instanceof SslSocketConnector)) {
                server.removeConnector(c);
            }
        }
        return server;
    }

    /**
     * Construct a Jetty Server instance.
     */
    private static Server jettyCreateServer(Integer port, String host, Integer httpsPort) {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(Utils.getInt(port, 80));
        connector.setHost(host);
        connector.setMaxIdleTime(200000);

        Server server = new Server();
        server.addConnector(connector);
        server.setSendDateHeader(true);

        if (httpsPort != null && httpsPort > 0) {
            removeNonSslConnector(server);
        }
        return server;
    }

    /**
     * Modified version of run-jetty
     * Assumes configurator sets handler.
     */
    public static void stormRunJetty(Integer port, String host, Integer httpsPort, IConfigurator configurator) throws Exception {
        Server s = jettyCreateServer(port, host, httpsPort);
        if (configurator != null) {
            configurator.execute(s);
        }
        s.start();
    }

    public static void stormRunJetty(Integer port, IConfigurator configurator) throws Exception {
        stormRunJetty(port, null, null, configurator);
    }

    public static String wrapJsonInCallback(String callback, String response) {
        return callback + "(" + response + ");";
    }

    public static Map jsonResponse(Object data, String callback) {
        return jsonResponse(data, callback, true, null, null);
    }

    public static Map jsonResponse(Object data, String callback, Long status) {
        return jsonResponse(data, callback, true, status, null);
    }

    public static Map jsonResponse(Object data, String callback, Map headers) {
        return jsonResponse(data, callback, true, null, headers);
    }

    public static Map jsonResponse(Object data, String callback, boolean needSerialize, Long status, Map headers) {
        Map<String, String> headersResult = new HashMap<>();
        headersResult.put("Cache-Control", "no-cache, no-store");
        headersResult.put("Access-Control-Allow-Origin", "*");
        headersResult.put("Access-Control-Allow-Headers", "Content-Type, Access-Control-Allow-Headers, Access-Controler-Allow-Origin, X-Requested-By, X-Csrf-Token, Authorization, X-Requested-With");
        if (callback != null) {
            headersResult.put("Content-Type", "application/javascript;charset=utf-8");
        } else {
            headersResult.put("Content-Type", "application/json;charset=utf-8");
        }
        if (headers != null) {
            headersResult.putAll(headers);
        }

        String serializedData;
        if (needSerialize) {
            serializedData = JSONValue.toJSONString(data);
        } else {
            serializedData = (String) data;
        }

        String body;
        if (callback != null) {
            body = wrapJsonInCallback(callback, serializedData);
        } else {
            body = serializedData;
        }

        return ImmutableMap.of(
                keyword("status"), Utils.getInt(status, 200),
                keyword("headers"), headersResult,
                keyword("body"), body
        );
    }

    public static Map exceptionToJson(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return ImmutableMap.of("error", "Internal Server Error", "errorMessage", sw.toString());
    }

    private static Keyword keyword(String key) {
        return RT.keyword(null, key);
    }
}
