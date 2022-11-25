package org.apache.hadoop.ozone.s3secret;


import org.apache.hadoop.ozone.om.helpers.S3SecretValue;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("/secret/generate")
public class S3SecretGenerateEndpoint extends S3SecretEndpointBase {

  @GET
  public Response get() throws IOException {
    S3SecretResponse s3SecretResponse = new S3SecretResponse();
    S3SecretValue s3SecretValue = generateS3Secret();
    s3SecretResponse.setAwsSecret(s3SecretValue.getAwsSecret());
    s3SecretResponse.setAwsAccessKey(s3SecretValue.getAwsAccessKey());
    return Response.ok(s3SecretResponse).build();
  }


  protected S3SecretValue generateS3Secret() throws IOException {
    return client.getObjectStore().getS3Secret(shortNameFromRequest());
  }
}
