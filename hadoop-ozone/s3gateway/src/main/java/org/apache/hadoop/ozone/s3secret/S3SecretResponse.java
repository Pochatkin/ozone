package org.apache.hadoop.ozone.s3secret;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "S3Secret")
public class S3SecretResponse {
  @XmlElement(name = "awsAccessKey")
  private String awsAccessKey;

  @XmlElement(name = "awsSecret")
  private String awsSecret;

  public String getAwsAccessKey() {
    return awsAccessKey;
  }

  public String getAwsSecret() {
    return awsSecret;
  }

  public void setAwsAccessKey(String awsAccessKey) {
    this.awsAccessKey = awsAccessKey;
  }

  public void setAwsSecret(String awsSecret) {
    this.awsSecret = awsSecret;
  }
}
