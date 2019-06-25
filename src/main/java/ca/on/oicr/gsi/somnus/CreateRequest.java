package ca.on.oicr.gsi.somnus;

import java.util.List;

public class CreateRequest {
  private String creator;
  private String environment;
  private String reason;
  private List<String> services;
  private long ttl;

  public String getCreator() {
    return creator;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getReason() {
    return reason;
  }

  public List<String> getServices() {
    return services;
  }

  public long getTtl() {
    return ttl;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public void setServices(List<String> services) {
    this.services = services;
  }

  public void setTtl(long ttl) {
    this.ttl = ttl;
  }
}
