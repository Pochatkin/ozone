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

package org.apache.hadoop.ozone.s3secret;

import org.apache.hadoop.hdds.conf.MutableConfigurationSource;
import org.apache.hadoop.hdds.server.http.BaseHttpServer;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;

/**
 * Http server for processing operation related to S3 secrets.
 */
public class S3SecretHttpServer extends BaseHttpServer {
  
  public S3SecretHttpServer(MutableConfigurationSource conf, String name)
      throws IOException {
    super(conf, name);
  }

  @Override
  public boolean isSecurityEnabled() {
    return UserGroupInformation.isSecurityEnabled();
  }

  @Override
  protected String getHttpAddressKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_ADDRESS_KEY;
  }

  @Override
  protected String getHttpBindHostKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_BIND_HOST_KEY;
  }

  @Override
  protected String getHttpsAddressKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTPS_ADDRESS_KEY;
  }

  @Override
  protected String getHttpsBindHostKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTPS_BIND_HOST_KEY;
  }

  @Override
  protected String getBindHostDefault() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_BIND_HOST_DEFAULT;
  }

  @Override
  protected int getHttpBindPortDefault() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_BIND_PORT_DEFAULT;
  }

  @Override
  protected int getHttpsBindPortDefault() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTPS_BIND_PORT_DEFAULT;
  }

  @Override
  protected String getKeytabFile() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_KEYTAB_FILE;
  }

  @Override
  protected String getSpnegoPrincipal() {
    return OzoneS3SecretConfigKeys
        .OZONE_S3G_SECRET_WEB_AUTHENTICATION_KERBEROS_PRINCIPAL;
  }

  @Override
  protected String getEnabledKey() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_ENABLED_KEY;
  }

  @Override
  protected String getHttpAuthType() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_AUTH_TYPE;
  }

  @Override
  protected String getHttpAuthConfigPrefix() {
    return OzoneS3SecretConfigKeys.OZONE_S3G_SECRET_HTTP_AUTH_CONFIG_PREFIX;
  }
}
