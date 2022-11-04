package ca.on.oicr.gsi.somnus;

import com.fasterxml.jackson.annotation.JsonGetter;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

class Inhibition implements Predicate<String>, Iterable<String> {
  private static final AtomicInteger idGenerator = new AtomicInteger();
  private boolean awoken;
  private final Instant created = Instant.now();
  private final String creator;
  private final String environment;
  private final Instant expirationTime;
  private final int id = idGenerator.getAndIncrement();
  private final String reason;
  private final Set<String> services;

  public Inhibition(
      Set<String> services,
      String environment,
      Instant expirationTime,
      String creator,
      String reason) {
    this.services = services;
    this.environment = environment;
    this.expirationTime = expirationTime;
    this.creator = creator;
    this.reason = reason;
  }

  public boolean awoken() {
    return awoken;
  }

  @JsonGetter("created")
  public Instant created() {
    return created;
  }

  @JsonGetter("creator")
  public String creator() {
    return creator;
  }

  @JsonGetter("environment")
  public String environment() {
    return environment;
  }

  @JsonGetter("expirationTime")
  public Instant expirationTime() {
    return expirationTime;
  }

  public int id() {
    return id;
  }

  @Override
  public Iterator<String> iterator() {
    return services.iterator();
  }

  @JsonGetter("reason")
  public String reason() {
    return reason;
  }

  @JsonGetter("services")
  private Set<String> services() {
    return services;
  }

  @Override
  public boolean test(String s) {
    return services.contains(s);
  }

  public void wake() {
    awoken = true;
  }
}
