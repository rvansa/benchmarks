RESTful performance benchmarks
==============================

Usage:

1. make sure that `WILDFLY_HOME`/`JBOSS_HOME` is set and start WildFly server
2. run `mvn install` (or `mvn verify`)

The application will be automatically deployed and all tests will be executed.
Options:
* `-Dgatling.simulationClass=com.example.MyTest` - run only single test
* `-Dtest.rps=100`                               - number of requests per seconds
* `-Dtest.duration=2`                            - length of the test (in seconds)
* `-Dtest.rampUp=  2`                            - length of the ramp-up (in seconds)

In the end, statistics from all tests will be printed out. If you want to take a look
on Gatling reports, you'll find these in target/gatling/results.

Running on two machines mode
-----------------------------
In case that you want to run Wildfly server with the application on different machines,
deploy the web application on server using
```
mvn install -Dserver
```
and on the client machine run

```
mvn install -Dtest.host=serveraddress -P !server-undeploy
```
(Note: sorry about the explicit profile, but Maven does not allow to deactivate from multiple properties)
You can also specify the server address using `-Dtest.port=1234` if it is not running on port 8080.

After your tests are finished, you can undeploy the web application from server using

```
mvn wildfly:undeploy
```