# Module `reservation`
Module `com.rezhub.reservation` is a Kalix project. It contains the framework to create a Kalix application by adding Kalix components.  
To understand more about these components, see [Developing services](https://docs.kalix.io/services/) and check
Spring-SDK [official documentation](https://docs.kalix.io/spring/index.html).  
For basic Kalix concepts, see [Designing services](https://docs.kalix.io/services/development-process.html).
Examples can be found [here](https://github.com/lightbend/kalix-jvm-sdk/tree/main/samples) in the folders with "spring" in their name.

## Build
Use Maven to compile this module:

```shell
mvn compile
```
or compile all modules in the parent Maven project:
```shell
cd .. # that is: go to the Maven parent project directory rez/reservation
mvn compile
```

Use `mvn install` to build.

## Run
To run the example locally, you must run the Kalix runtime.  
The included `docker-compose` file contains the configuration required to run the runtime locally.
It also contains the configuration to start a local Google Pub/Sub emulator that the Kalix proxy will connect to.
To start the proxy, run the following command from this directory:

```shell
docker-compose up
```

To start the application locally, the `spring-boot-maven-plugin` can be used:

```shell
mvn spring-boot:run
```

With both the runtime (a.k.a. proxy) and your application running, once you have defined endpoints they should be available at `http://localhost:9000`.


To deploy your service, install the `kalix` CLI as documented in
[Setting up a local development environment](https://docs.kalix.io/setting-up/)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://docs.kalix.io/projects/container-registries.html)
for more information on how to make your docker image available to Kalix.

Finally, you can use the [Kalix Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Kalix, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `kalix` CLI.

## Integration Tests
Integration Tests are present inside `src/it/java`.  
They are run by the maven failsafe plugin.  
Run them with:
```
mvn verify -Pit
```

## Deploy
```shell
kalix secret create generic msg-creds --literal INSTALL_TOKEN=xxxxxxxxxxxxx
mvn deploy
# grab correct tag (i.e. 0.5) and paste it here:
kalix service deploy reservation registry.hub.docker.com/max8github/reservation:0.5 --secret-env INSTALL_TOKEN=msg-creds/INSTALL_TOKEN
kalix services list           # to check status
kalix services get reservation   # to check status
```

## Prod
To expose the service, do:
```shell
kalix services expose reservation  # --enable-cors
   The service 'reservation' was successfully exposed at: name1.name2.kalix.app
```