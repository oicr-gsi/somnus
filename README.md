# Somnus: Putting Other Services to Sleep
We use [Prometheus](http://prometheus.io) to monitor our systems and can use
this feedback to inhibit other processes during overload conditions.

For instance, we monitor load on a database used by many applications and stop
running low priority applications when the database is very busy. We accomplish
this by firing an alert named `AutoInhibit` with a label `scope` that describes
the service or resource that is unavailable. Applications check for firing
`AutoInhibit` alerts and stop access those applications while the alert is
firing.

Somnus is an application to allow manually inhibiting applications using this
mechanism. It provides a user interface and REST API to create inhibitions to
block services. This works is conceptually similar to Alert Manager silences.

To run a Somnus instance, create a file with all of the `environment` labels
and a file with the name of all the `scope` labels you want users to be able to
inhibit; one entry per line. Somnus can create inhibitions for other
environments and services by the REST API, but uses only previously described
services for the UI.

To run:

    java -jar somnus.jar 8080 /path/to/environment/list /path/to/known/service/list

where 8080 is the desired port.

Once running, monitor it via Prometheus and then add a rule to fire alerts. See
[somnus.rules.yaml] for an example.
