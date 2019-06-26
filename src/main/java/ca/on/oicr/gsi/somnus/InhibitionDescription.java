package ca.on.oicr.gsi.somnus;

import java.time.Duration;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

class InhibitionDescription implements Comparable<InhibitionDescription> {
  private enum Status {
    SLEEPING("Sleeping", 0),
    AWOKEN_MANUAL("Awoken – Manually", 1),
    AWOKEN_EXPIRED("Awoken – Expired", 1);

    public static Status of(Inhibition inhibition, Instant now) {
      if (inhibition.awoken()) {
        return AWOKEN_MANUAL;
      }
      return inhibition.expirationTime().isAfter(now) ? SLEEPING : AWOKEN_EXPIRED;
    }

    private final String description;
    private final int sortOrder;

    Status(String description, int sortOrder) {
      this.description = description;
      this.sortOrder = sortOrder;
    }
  }

  private final Inhibition inhibition;
  private final Instant now;
  private final Status status;

  public InhibitionDescription(Inhibition inhibition, Instant now) {
    this(inhibition, now, Status.of(inhibition, now));
  }

  private InhibitionDescription(Inhibition inhibition, Instant now, Status status) {
    this.inhibition = inhibition;
    this.now = now;
    this.status = status;
  }

  @Override
  public int compareTo(InhibitionDescription other) {
    int result = Integer.compare(status.sortOrder, other.status.sortOrder);
    if (result == 0) {
      result = other.inhibition.expirationTime().compareTo(inhibition.expirationTime());
    }
    if (result == 0) {
      result = other.inhibition.created().compareTo(inhibition.created());
    }
    return result;
  }

  public void emit(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement("div");
    if (status == Status.SLEEPING) {
      writer.writeAttribute("class", "sleeping");
    }
    writer.writeStartElement("h1");
    writer.writeAttribute("id", "inhibition" + inhibition.id());
    writer.writeCharacters(String.format("Inhibition %d", inhibition.id()));
    writer.writeEndElement();

    writer.writeStartElement("table");
    line(writer, "Status", status.description);
    line(writer, "Creator", inhibition.creator());
    line(writer, "Created", inhibition.created().toString());
    line(writer, "Environment", inhibition.environment());
    if (status == Status.SLEEPING) {
      line(writer, "Expires", Duration.between(now, inhibition.expirationTime()).toString());
      writer.writeStartElement("tr");
      writer.writeStartElement("td");
      writer.writeComment("");
      writer.writeEndElement();
      writer.writeStartElement("td");
      writer.writeStartElement("span");
      writer.writeAttribute("class", "button");
      writer.writeAttribute("onclick", String.format("wake(%d)", inhibition.id()));
      writer.writeCharacters("Wake up");
      writer.writeEndElement();
      writer.writeEndElement();
      writer.writeEndElement();
    }
    line(writer, "Reason", inhibition.reason());
    for (final String service : inhibition) {
      line(writer, "Service", service);
    }
    writer.writeEndElement();
    writer.writeEndElement();
  }

  private void line(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
    writer.writeStartElement("tr");
    writer.writeStartElement("td");
    writer.writeCharacters(name);
    writer.writeEndElement();
    writer.writeStartElement("td");
    writer.writeCharacters(value);
    writer.writeEndElement();
    writer.writeEndElement();
  }
}
