package org.apache.hadoop.ozone.s3secret;

public final class OzoneS3SecretConfigKeys {
  public static final String OZONE_S3_SECRET_HTTP_BIND_HOST_KEY =
      "ozone.s3.secret.http-bind-host";
  public static final String OZONE_S3_SECRET_HTTPS_BIND_HOST_KEY =
      "ozone.s3.secret.https-bind-host";

  public static final String OZONE_S3_SECRET_HTTP_ADDRESS_KEY =
      "ozone.s3.secret.http-address";
  public static final String OZONE_S3_SECRET_HTTPS_ADDRESS_KEY =
      "ozone.s3.secret.https-address";
  public static final String OZONE_S3G_HTTP_BIND_HOST_DEFAULT =
      "0.0.0.0";

  public static final int OZONE_S3G_SECRET_HTTP_PORT_DEFAULT = 9880;
  public static final int OZONE_S3G_SECRET_HTTPS_PORT_DEFAULT = 9881;

  public static final String OZONE_S3_SECRET_KEYTAB_FILE =
      "ozone.s3.secret.http.auth.kerberos.keytab";
  public static final String OZONE_S3_SECRET_PRINCIPAL =
      "ozone.s3.secret.http.auth.kerberos.principal";
}
