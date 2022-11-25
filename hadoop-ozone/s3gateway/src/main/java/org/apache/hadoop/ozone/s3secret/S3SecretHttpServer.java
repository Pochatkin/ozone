package org.apache.hadoop.ozone.s3secret;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.conf.MutableConfigurationSource;
import org.apache.hadoop.hdds.server.http.HttpConfig;
import org.apache.hadoop.hdds.server.http.HttpServer2;
import org.apache.hadoop.hdds.utils.LegacyHadoopConfigurationSource;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.ozone.OzoneConfigKeys;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Optional;
import java.util.OptionalInt;

import static org.apache.hadoop.hdds.HddsUtils.*;
import static org.apache.hadoop.hdds.server.http.HttpConfig.getHttpPolicy;
import static org.apache.hadoop.ozone.OzoneConfigKeys.*;

public class S3SecretHttpServer {
  private static final Logger LOG =
      LoggerFactory.getLogger(S3SecretHttpServer.class);
  private static final String JETTY_BASETMPDIR =
      "org.eclipse.jetty.webapp.basetempdir";

  private final HttpServer2 httpServer;
  private final MutableConfigurationSource conf;
  private final HttpConfig.Policy policy;
  private final String name;

  private InetSocketAddress httpAddress;
  private InetSocketAddress httpsAddress;

  public S3SecretHttpServer(MutableConfigurationSource conf, String name)
      throws IOException {
    this.name = name;
    this.conf = conf;
    policy = getHttpPolicy(conf);
    if (!UserGroupInformation.isSecurityEnabled()) {
      throw new S3ServerNotInitializedException();
    }
    this.httpAddress = getHttpBindAddress();
    this.httpsAddress = getHttpsBindAddress();

    // Avoid registering o.a.h.http.PrometheusServlet in HttpServer2.
    // TODO: Replace "hadoop.prometheus.endpoint.enabled" with
    // CommonConfigurationKeysPublic.HADOOP_PROMETHEUS_ENABLED when possible.
    conf.set("hadoop.prometheus.endpoint.enabled", "false");

    HttpServer2.Builder builder = newHttpServer2BuilderForOzone(
        conf, httpAddress, httpsAddress, name);

    LOG.info("HttpAuthType: {} = {}", "kerberos", "kerberos");
    // Ozone config prefix must be set to avoid AuthenticationFilter
    // fall back to default one form hadoop.http.authentication.
    builder.authFilterConfigurationPrefix("");
    builder.setSecurityEnabled(true);
    builder.setUsernameConfKey(getSpnegoPrincipal());
    builder.setKeytabConfKey(getKeytabFile());

    httpServer = builder.build();

    String baseDir = conf.get(OzoneConfigKeys.OZONE_HTTP_BASEDIR);
    if (!StringUtils.isEmpty(baseDir)) {
      createDir(baseDir);
      httpServer.setAttribute(JETTY_BASETMPDIR, baseDir);
      LOG.info("HTTP server of {} uses base directory {}", name, baseDir);
    }
  }

  /**
   * Return a HttpServer.Builder that the OzoneManager/SCM/Datanode/S3Gateway/
   * Recon to initialize their HTTP / HTTPS server.
   */
  public static HttpServer2.Builder newHttpServer2BuilderForOzone(
      MutableConfigurationSource conf, final InetSocketAddress httpAddr,
      final InetSocketAddress httpsAddr, String name) {
    HttpConfig.Policy policy = getHttpPolicy(conf);

    HttpServer2.Builder builder = new HttpServer2.Builder().setName(name)
        .setConf(conf);

    // initialize the webserver for uploading/downloading files.
    if (policy.isHttpEnabled()) {
      if (httpAddr.getPort() == 0) {
        builder.setFindPort(true);
      }

      URI uri = URI.create("http://" + NetUtils.getHostPortString(httpAddr));
      builder.addEndpoint(uri);
      LOG.info("Starting Web-server for {} at: {}", name, uri);
    }

    if (policy.isHttpsEnabled() && httpsAddr != null) {
      ConfigurationSource sslConf = loadSslConfiguration(conf);
      loadSslConfToHttpServerBuilder(builder, sslConf);

      if (httpsAddr.getPort() == 0) {
        builder.setFindPort(true);
      }

      URI uri = URI.create("https://" + NetUtils.getHostPortString(httpsAddr));
      builder.addEndpoint(uri);
      LOG.info("Starting Web-server for {} at: {}", name, uri);
    }
    return builder;
  }


  /**
   * Add a servlet to BaseHttpServer.
   *
   * @param servletName The name of the servlet
   * @param pathSpec    The path spec for the servlet
   * @param clazz       The servlet class
   */
  protected void addServlet(String servletName, String pathSpec,
                            Class<? extends HttpServlet> clazz) {
    httpServer.addServlet(servletName, pathSpec, clazz);
  }

  protected void addInternalServlet(String servletName, String pathSpec,
                                    Class<? extends HttpServlet> clazz) {
    httpServer.addInternalServlet(servletName, pathSpec, clazz);
  }

  protected InetSocketAddress getBindAddress(String bindHostKey,
                                             String addressKey, String bindHostDefault, int bindPortdefault) {
    final Optional<String> bindHost =
        getHostNameFromConfigKeys(conf, bindHostKey);

    final OptionalInt addressPort =
        getPortNumberFromConfigKeys(conf, addressKey);

    final Optional<String> addressHost =
        getHostNameFromConfigKeys(conf, addressKey);

    String hostName = bindHost.orElse(addressHost.orElse(bindHostDefault));

    return NetUtils.createSocketAddr(
        hostName + ":" + addressPort.orElse(bindPortdefault));
  }

  /**
   * Retrieve the socket address that should be used by clients to connect
   * to the  HTTPS web interface.
   *
   * @return Target InetSocketAddress for the Ozone HTTPS endpoint.
   */
  public InetSocketAddress getHttpsBindAddress() {
    return getBindAddress(getHttpsBindHostKey(), getHttpsAddressKey(),
        getBindHostDefault(), getHttpsBindPortDefault());
  }

  /**
   * Retrieve the socket address that should be used by clients to connect
   * to the  HTTP web interface.
   * <p>
   * * @return Target InetSocketAddress for the Ozone HTTP endpoint.
   */
  public InetSocketAddress getHttpBindAddress() {
    return getBindAddress(getHttpBindHostKey(), getHttpAddressKey(),
        getBindHostDefault(), getHttpBindPortDefault());

  }

  public void start() throws IOException {
    if (httpServer != null) {
      httpServer.start();
      updateConnectorAddress();
    }

  }

  public void stop() throws Exception {
    if (httpServer != null) {
      httpServer.stop();
    }
  }

  /**
   * Update the configured listen address based on the real port
   * <p>
   * (eg. replace :0 with real port)
   */
  public void updateConnectorAddress() {
    int connIdx = 0;
    if (policy.isHttpEnabled()) {
      httpAddress = httpServer.getConnectorAddress(connIdx++);
      String realAddress = NetUtils.getHostPortString(httpAddress);
      conf.set(getHttpAddressKey(), realAddress);
      LOG.info("HTTP server of {} listening at http://{}", name, realAddress);
    }

    if (policy.isHttpsEnabled()) {
      httpsAddress = httpServer.getConnectorAddress(connIdx);
      String realAddress = NetUtils.getHostPortString(httpsAddress);
      conf.set(getHttpsAddressKey(), realAddress);
      LOG.info("HTTPS server of {} listening at https://{}", name, realAddress);
    }
  }


  public static HttpServer2.Builder loadSslConfToHttpServerBuilder(
      HttpServer2.Builder builder, ConfigurationSource sslConf) {
    return builder
        .needsClientAuth(
            sslConf.getBoolean(OZONE_CLIENT_HTTPS_NEED_AUTH_KEY,
                OZONE_CLIENT_HTTPS_NEED_AUTH_DEFAULT))
        .keyPassword(getPassword(sslConf, OZONE_SERVER_HTTPS_KEYPASSWORD_KEY))
        .keyStore(sslConf.get("ssl.server.keystore.location"),
            getPassword(sslConf, OZONE_SERVER_HTTPS_KEYSTORE_PASSWORD_KEY),
            sslConf.get("ssl.server.keystore.type", "jks"))
        .trustStore(sslConf.get("ssl.server.truststore.location"),
            getPassword(sslConf, OZONE_SERVER_HTTPS_TRUSTSTORE_PASSWORD_KEY),
            sslConf.get("ssl.server.truststore.type", "jks"))
        .excludeCiphers(
            sslConf.get("ssl.server.exclude.cipher.list"));
  }

  /**
   * Leverages the Configuration.getPassword method to attempt to get
   * passwords from the CredentialProvider API before falling back to
   * clear text in config - if falling back is allowed.
   *
   * @param conf  Configuration instance
   * @param alias name of the credential to retrieve
   * @return String credential value or null
   */
  static String getPassword(ConfigurationSource conf, String alias) {
    String password = null;
    try {
      char[] passchars = conf.getPassword(alias);
      if (passchars != null) {
        password = new String(passchars);
      }
    } catch (IOException ioe) {
      LOG.warn("Setting password to null since IOException is caught"
          + " when getting password", ioe);
    }
    return password;
  }
  /**
   * Load HTTPS-related configuration.
   */
  public static ConfigurationSource loadSslConfiguration(
      ConfigurationSource conf) {
    Configuration sslConf =
        new Configuration(false);

    sslConf.addResource(conf.get(
        OzoneConfigKeys.OZONE_SERVER_HTTPS_KEYSTORE_RESOURCE_KEY,
        OzoneConfigKeys.OZONE_SERVER_HTTPS_KEYSTORE_RESOURCE_DEFAULT));

    final String[] reqSslProps = {
        OzoneConfigKeys.OZONE_SERVER_HTTPS_TRUSTSTORE_LOCATION_KEY,
        OzoneConfigKeys.OZONE_SERVER_HTTPS_KEYSTORE_LOCATION_KEY,
        OzoneConfigKeys.OZONE_SERVER_HTTPS_KEYSTORE_PASSWORD_KEY,
        OzoneConfigKeys.OZONE_SERVER_HTTPS_KEYPASSWORD_KEY
    };

    // Check if the required properties are included
    for (String sslProp : reqSslProps) {
      if (sslConf.get(sslProp) == null) {
        LOG.warn("SSL config {} is missing. If {} is specified, make sure it "
                + "is a relative path", sslProp,
            OzoneConfigKeys.OZONE_SERVER_HTTPS_KEYSTORE_RESOURCE_KEY);
      }
    }

    boolean requireClientAuth = conf.getBoolean(
        OZONE_CLIENT_HTTPS_NEED_AUTH_KEY, OZONE_CLIENT_HTTPS_NEED_AUTH_DEFAULT);
    sslConf.setBoolean(OZONE_CLIENT_HTTPS_NEED_AUTH_KEY, requireClientAuth);
    return new LegacyHadoopConfigurationSource(sslConf);
  }

  public InetSocketAddress getHttpAddress() {
    return httpAddress;
  }

  public InetSocketAddress getHttpsAddress() {
    return httpsAddress;
  }

  protected String getHttpAddressKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3_SECRET_HTTP_ADDRESS_KEY;
  }

  protected String getHttpsAddressKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3_SECRET_HTTPS_ADDRESS_KEY;
  }

  protected String getHttpBindHostKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3_SECRET_HTTP_BIND_HOST_KEY;
  }

  protected String getHttpsBindHostKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3_SECRET_HTTPS_BIND_HOST_KEY;
  }

  protected String getBindHostDefault() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_HTTP_BIND_HOST_DEFAULT;
  }

  protected int getHttpBindPortDefault() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_PORT_DEFAULT;
  }

  protected int getHttpsBindPortDefault() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTPS_PORT_DEFAULT;
  }

  protected String getKeytabFile() {
    return OzoneS3SecretConfigKeys.OZONE_S3_SECRET_KEYTAB_FILE;
  }

  protected String getSpnegoPrincipal() {
    return OzoneS3SecretConfigKeys.OZONE_S3_SECRET_PRINCIPAL;
  }
}
