package org.apache.hadoop.ozone.s3secret;

import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.ozone.audit.AuditAction;
import org.apache.hadoop.ozone.audit.AuditEventStatus;
import org.apache.hadoop.ozone.audit.AuditMessage;
import org.apache.hadoop.ozone.audit.Auditor;
import org.apache.hadoop.ozone.client.OzoneClient;
import org.apache.hadoop.ozone.om.helpers.S3SecretValue;
import org.apache.hadoop.ozone.s3.util.AuditUtils;
import org.apache.hadoop.security.HadoopKerberosName;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.util.Map;

public class S3SecretEndpointBase implements Auditor {
  @Context
  protected ContainerRequestContext context;

  @Inject
  protected OzoneConfiguration ozoneConfiguration;

  @Inject
  protected OzoneClient client;

  protected String shortNameFromRequest() throws IOException {
    String principal = context.getSecurityContext().getUserPrincipal().getName();
    return new HadoopKerberosName(principal).getShortName();
  }

  private AuditMessage.Builder auditMessageBaseBuilder(AuditAction op,
                                                       Map<String, String> auditMap) {
    AuditMessage.Builder builder = new AuditMessage.Builder()
        .forOperation(op)
        .withParams(auditMap);
    if (context != null) {
      builder.atIp(AuditUtils.getClientIpAddress(context));
    }
    return builder;
  }

  @Override
  public AuditMessage buildAuditMessageForSuccess(AuditAction op,
                                                  Map<String, String> auditMap) {
    AuditMessage.Builder builder = auditMessageBaseBuilder(op, auditMap)
        .withResult(AuditEventStatus.SUCCESS);
    return builder.build();
  }

  @Override
  public AuditMessage buildAuditMessageForFailure(AuditAction op,
                                                  Map<String, String> auditMap, Throwable throwable) {
    AuditMessage.Builder builder = auditMessageBaseBuilder(op, auditMap)
        .withResult(AuditEventStatus.FAILURE)
        .withException(throwable);
    return builder.build();
  }
}
