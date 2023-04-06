/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.ozone.s3;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.hadoop.hdds.conf.MutableConfigurationSource;
import org.apache.hadoop.hdds.server.http.BaseHttpServer;
import org.apache.hadoop.hdds.server.http.ServletElementsFactory;
import org.apache.hadoop.ozone.s3secret.OzoneS3SecretConfigKeys;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.authentication.server.AuthenticationFilter;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;

/**
 * S3 Gateway specific configuration keys.
 */
public class S3GatewayHttpServer extends BaseHttpServer {

  /**
   * Default offset between two filters.
   */
  public static final int FILTER_PRIORITY_DO_AFTER = 50;

  public S3GatewayHttpServer(MutableConfigurationSource conf,
                             String name) throws IOException {
    super(conf, name);
    addServlet("icon", "/favicon.ico", IconServlet.class);
    addSecretAuthentication(conf);
  }

  private void addSecretAuthentication(MutableConfigurationSource conf) throws IOException {
    ServletHandler handler = getWebAppContext().getServletHandler();

    Map<String, String> params = new HashMap<>();
    String principalInConf = conf.get(OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_WEB_AUTHENTICATION_KERBEROS_PRINCIPAL);
    if (principalInConf != null && !principalInConf.isEmpty()) {
      params.put("kerberos.principal", SecurityUtil.getServerPrincipal(
          principalInConf, conf.get(OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_BIND_HOST_KEY)));
    }
    String httpKeytab = conf.get(OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_KEYTAB_FILE);
    if (httpKeytab != null && !httpKeytab.isEmpty()) {
      params.put("kerberos.keytab", httpKeytab);
    }
    params.put(AuthenticationFilter.AUTH_TYPE, "kerberos");

    FilterHolder holder = ServletElementsFactory.createFilterHolder("secretAuthentication",
        AuthenticationFilter.class.getName(), params);
    FilterMapping filterMapping = ServletElementsFactory.createFilterMapping("secretAuthentication",
        new String[]{"/secret/*"});

    handler.addFilter(holder, filterMapping);
  }

  @Override
  protected String getHttpAddressKey() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTP_ADDRESS_KEY;
  }

  @Override
  protected String getHttpBindHostKey() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTP_BIND_HOST_KEY;
  }

  @Override
  protected String getHttpsAddressKey() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTPS_ADDRESS_KEY;
  }

  @Override
  protected String getHttpsBindHostKey() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTPS_BIND_HOST_KEY;
  }

  @Override
  protected String getBindHostDefault() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTP_BIND_HOST_DEFAULT;
  }

  @Override
  protected int getHttpBindPortDefault() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTP_BIND_PORT_DEFAULT;
  }

  @Override
  protected int getHttpsBindPortDefault() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTPS_BIND_PORT_DEFAULT;
  }

  @Override
  protected String getKeytabFile() {
    return S3GatewayConfigKeys.OZONE_S3G_KEYTAB_FILE;
  }

  @Override
  protected String getSpnegoPrincipal() {
    return S3GatewayConfigKeys.OZONE_S3G_WEB_AUTHENTICATION_KERBEROS_PRINCIPAL;
  }

  @Override
  protected String getEnabledKey() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTP_ENABLED_KEY;
  }

  @Override
  protected String getHttpAuthType() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTP_AUTH_TYPE;
  }

  @Override
  protected String getHttpAuthConfigPrefix() {
    return S3GatewayConfigKeys.OZONE_S3G_HTTP_AUTH_CONFIG_PREFIX;
  }

  /**
   * Servlet for favicon.ico.
   */
  public static class IconServlet extends HttpServlet {
    private static final long serialVersionUID = -1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      response.setContentType("image/png");
      response.sendRedirect("/static/images/ozone.ico");
    }
  }
}
