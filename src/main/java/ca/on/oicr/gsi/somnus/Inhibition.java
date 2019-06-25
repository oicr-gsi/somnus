package ca.on.oicr.gsi.somnus;

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

  public void awake() {
    awoken = true;
  }

  public boolean awoken() {
    return awoken;
  }

  public Instant created() {
    return created;
  }

  public String creator() {
    return creator;
  }

  public String environment() {
    return environment;
  }

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

  public String reason() {
    return reason;
  }

  @Override
  public boolean test(String s) {
    return services.contains(s);
  }
}
