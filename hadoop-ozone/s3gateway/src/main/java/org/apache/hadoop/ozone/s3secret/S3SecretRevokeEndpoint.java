package org.apache.hadoop.ozone.s3secret;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("/secret/revoke")
public class S3SecretRevokeEndpoint extends S3SecretEndpointBase {

  @GET
  public Response get() throws IOException {
    revokeSecret();
    return Response.ok().build();
  }

  private void revokeSecret() throws IOException {
    client.getObjectStore().revokeS3Secret(shortNameFromRequest());
  }

}
