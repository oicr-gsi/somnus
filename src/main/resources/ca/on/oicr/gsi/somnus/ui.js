export default function(form, environments, services) {
  const name = document.createElement("INPUT");
  name.type = "text";
  name.value = window.localStorage.getItem("creator");
  form.appendChild(document.createTextNode("Creator: "));
  form.appendChild(name);
  form.appendChild(document.createElement("BR"));

  const reason = document.createElement("INPUT");
  reason.type = "text";
  form.appendChild(document.createTextNode("Reason: "));
  form.appendChild(reason);
  form.appendChild(document.createElement("BR"));

  const ttl = document.createElement("INPUT");
  ttl.type = "number";
  ttl.value = "1";
  const units = document.createElement("SELECT");
  for (const [name, multiplier] of [
    ["days", 86400],
    ["hours", 3600],
    ["minutes", 60],
    ["seconds", 1]
  ]) {
    const option = document.createElement("OPTION");
    option.text = name;
    option.value = multiplier;
    units.appendChild(option);
  }
  units.selectedIndex = 1;
  form.appendChild(document.createTextNode("Time-to-quiet: "));
  form.appendChild(ttl);
  form.appendChild(units);
  form.appendChild(document.createElement("BR"));

  const environment = document.createElement("SELECT");
  for (const name of environments) {
    const option = document.createElement("OPTION");
    option.text = name;
    option.value = name;
    environment.appendChild(option);
  }
  environment.value = window.localStorage.getItem("environment");
  form.appendChild(document.createTextNode("Environment: "));
  form.appendChild(environment);
  form.appendChild(document.createElement("BR"));

  const serviceSelection = new Map();
  for (const service of services) {
    const check = document.createElement("INPUT");
    check.type = "checkbox";
    const label = document.createElement("LABEL");
    label.appendChild(check);
    label.appendChild(document.createTextNode(service));
    form.appendChild(label);
    form.appendChild(document.createElement("BR"));
    serviceSelection.set(service, check);
  }

  const button = document.createElement("INPUT");
  button.type = "button";
  button.value = "Throw Shade";
  form.appendChild(button);
  button.addEventListener("click", () => {
    const request = {
      creator: name.value.trim(),
      environment: environment.value,
      reason: reason.value.trim(),
      services: Array.from(serviceSelection.entries())
        .filter(([name, checkbox]) => checkbox.checked)
        .map(([name, checkbox]) => name),
      ttl: ttl.valueAsNumber * parseInt(units.value)
    };
    if (
      !request.creator ||
      !request.environment ||
      !request.services.length ||
      isNaN(request.ttl) ||
      request.ttl < 1
    ) {
      alert("Invalid input");
      return;
    }
    window.localStorage.setItem("creator", request.creator);
    window.localStorage.setItem("environment", request.environment);
    fetch("/create", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(request)
    })
      .then(response => response.json())
      .then(response => {
        alert(
          `Added inhibition ${response.id} that will expire at ${new Date(
            response.expirationTime * 1000
          )}.`
        );
        window.location = "/";
      })
      .catch(error => alert(error));
  });
}
