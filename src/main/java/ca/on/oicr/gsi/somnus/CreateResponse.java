package ca.on.oicr.gsi.somnus;

public class CreateResponse {
  private long expirationTime;
  private int id;

  public long getExpirationTime() {
    return expirationTime;
  }

  public int getId() {
    return id;
  }

  public void setExpirationTime(long expirationTime) {
    this.expirationTime = expirationTime;
  }

  public void setId(int id) {
    this.id = id;
  }
}
