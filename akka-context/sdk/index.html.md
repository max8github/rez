<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)

<!-- </nav> -->

# Developing

|  | **New to Akka? Start here:**

Use the [Build your first agent](../getting-started/author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |
The Akka SDK provides you proven design patterns that enable your apps to remain responsive to change. It frees you from infrastructure concerns and lets you focus on the application logic.

With its few, concise components, the Akka SDK is easy to learn, and you can develop services in quick, iterative steps by running your code locally with full insight through Akka’s console.

Akka services let you build REST endpoints with flexible access control and multiple ways to expose these endpoints to their consuming systems or applications. Akka is secure by default, and you explicitly express the desired access through code and configuration.

Akka encapsulates data together with the logic to access and modify it. The data itself is expressed in regular Java records (plain old Java objects). The same goes for the events that change the data, these are expressed in pure Java to reflect business events that lead to data updates. Akka enables you to build fully event-driven services by combining logic and data into one thing: entities.

Data and changes to it are managed by Akka’s runtime without the need to manage database storage. Changes to your data can be automatically replicated to multiple places, not only within a single service, but also across applications and even cloud providers. An SQL-like language lets you design read access that ensures the data is properly indexed for your application needs.

Integrations with message systems like Kafka are already built-in and the Akka SDK enables message consumers to listen to topics and queues.

## <a href="about:blank#_prerequisites"></a> Prerequisites

The following are required to develop services with the Akka SDK:

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [Docker Engine](https://docs.docker.com/get-started/get-docker/) 27 or later

## <a href="about:blank#_getting_started"></a> Getting Started

Follow [Build your first agent](../getting-started/author-your-first-service.html) to implement your first agentic service. If you prefer to first explore working example code, you can check out [A simple shopping cart service](../getting-started/shopping-cart/build-and-deploy-shopping-cart.html) or our other [samples](../getting-started/samples.html).

On the other hand, if you would rather spend some time exploring our documentation, here are some main features you will find in this section:

- [Agents](agents.html)
- [Event Sourced Entities](event-sourced-entities.html)
- [Key Value Entities](key-value-entities.html)
- [HTTP Endpoints](http-endpoints.html)
- [gRPC Endpoints](grpc-endpoints.html)
- [MCP Endpoints](mcp-endpoints.html)
- [Views](views.html)
- [Workflows](workflows.html)
- [Timed Actions](timed-actions.html)
- [Consuming and Producing](consuming-producing.html)

<!-- <footer> -->
<!-- <nav> -->
[Access control lists](../concepts/acls.html) [Components](components/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->