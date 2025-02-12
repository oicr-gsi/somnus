export default function(form, environments, services) {
  const TTL_LOOKUP = {
    "minutes": 60,
    "hours": 3600,
    "days": 86400,
    "seconds": 1
  }

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
  for (const [name, value] of [
    ["minutes", "minutes"],
    ["hours", "hours"],
    ["days", "days"],
    ["seconds", "seconds"]
  ]) {
    const option = document.createElement("OPTION");
    option.text = name;
    option.value = value;
    units.appendChild(option);
  }
  units.selectedIndex = 0;
  units.addEventListener("input", calculateWarning);
  ttl.addEventListener("input", calculateWarning);
  form.appendChild(document.createTextNode("Time-to-quiet: "));
  form.appendChild(ttl);
  form.appendChild(units);
  var warning = document.createElement("span");
  warning.setAttribute("id", "warning");
  warning.setAttribute("style", "color:red;font-weight:bold")
  form.appendChild(warning);
  form.appendChild(document.createElement("BR"));

  const environment = document.createElement("SELECT");
  for (const name of environments.sort()) {
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
  for (const service of services.sort()) {
    const check = document.createElement("INPUT");
    check.type = "checkbox";
    const label = document.createElement("LABEL");
    label.appendChild(check);
    label.appendChild(document.createTextNode(service));
    form.appendChild(label);
    form.appendChild(document.createElement("BR"));
    serviceSelection.set(service, check);
  }
  form.appendChild(document.createTextNode("Other: "));
  const custom = document.createElement("INPUT");
  custom.type = "text";
  form.appendChild(custom);
  form.appendChild(document.createTextNode(" (space separated if multiple)"));
  form.appendChild(document.createElement("BR"));

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
        .map(([name, checkbox]) => name)
        .concat(custom.value.split(/\s+/).filter(x => !!x)),
      ttl: ttl.valueAsNumber * TTL_LOOKUP[units.value]
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

    if(units.value === "hours" || units.value === "days" || units.value === "seconds"){
      var sanityCheck = prompt("Please confirm the units you selected. (seconds, minutes, hours, days)");
      if (sanityCheck !== units.value){
        alert("Please double-check your inhibition time units.")
        return;
      }
    }
    window.localStorage.setItem("creator", request.creator);
    window.localStorage.setItem("environment", request.environment);
    fetch("/api/inhibitions", {
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
        window.location = `/view#inhibition${response.id}`;
      })
      .catch(error => alert(error));
  });

  function calculateWarning(){
    var ttlValue = ttl.valueAsNumber * TTL_LOOKUP[units.value];
    var warning = document.getElementById("warning");

    // Warn for very small inhibitions
    if (ttlValue < 60){
      warning.innerHTML = "Inhibition will be less than 1 minute; did you mean to choose a different unit?";

    // Warn for very large inhibitions (>= 20h)
    } else if (ttlValue >= 72000) {
      warning.innerHTML = "Inhibition will be over 20 hours; did you mean to choose a different unit?";
    } else {
      warning.innerHTML = "";
    }
  };
}

export function wake(id) {
  fetch("/api/inhibitions", {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(id)
  })
    .then(response => {
      if (response.status == 204) {
        window.location = "/view";
      } else {
        return response.json().then(response => {
          if (response.hasOwnProperty("error")) {
            alert(response.error);
          }
        });
      }
    })
    .catch(error => alert(error));
}

