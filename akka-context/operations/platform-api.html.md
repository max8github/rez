<!-- <nav> -->
- [Akka](../index.html)
- [Operating](index.html)
- [Akka Automated Operations](akka-platform.html)
- [Platform API](platform-api.html)

<!-- </nav> -->

# Platform API

The Akka Platform API is the set of gRPC and REST APIs that the Akka CLI and console use to manage organizations, projects, and services. Akka publishes the protobuf and OpenAPI schemas for these APIs, together with a Java client library, in the <a href="https://github.com/akka/platform-api">`akka/platform-api`</a> GitHub repository, so you can call the same APIs from your own tools and services.

## <a href="about:blank#_api_planes"></a> API planes

The Platform API has two planes:

Federation plane A gRPC API for managing projects, organizations, billing, users, and authentication. Served at `api.kalix.io:443`.

Control plane A REST API (OpenAPI) for managing resources within a project, such as services, routes, and secrets. Served at a region-specific endpoint, for example `https://api.gcp-us-east1.akka.io`.

## <a href="about:blank#_authentication"></a> Authentication

All API calls require a short-lived access token, obtained by one of the following mechanisms.

### <a href="about:blank#_refresh_token"></a> Refresh token

A refresh token is a long-lived token exchanged for a short-lived access token via the Auth API. There are two kinds:

User token Grants access to all projects belonging to your user account.

```command
akka auth tokens create --description "My refresh token"
```
Service token Grants access to a single project. Use this for CI/CD pipelines and other automation.

```command
akka projects tokens create --description "My service token"
```
Store the token value securely. It is shown only once.

### <a href="about:blank#_oauth_token_exchange_rfc_8693"></a> OAuth token exchange (RFC 8693)

Exchange an existing identity token, such as a GitHub Actions OIDC token or an Akka workload identity token, for an Akka access token using OAuth 2.0 token exchange. No long-lived Akka credential needs to be stored. The audience parameter identifies the configured OpenID Connect identity provider and is required.

To use this mechanism to authenticate an Akka service that calls the Platform API from another Akka service, configure [Akka platform workload identity](services/workload-identity.html#authenticating-with-the-akka-platform-api) on the calling service.

## <a href="about:blank#_java_client"></a> Java client

The <a href="https://github.com/akka/platform-api/tree/main/java-client">`java-client`</a> module provides a ready-to-use SDK that handles token acquisition and caching, gRPC channel management, and automatic routing of control plane requests to the correct regional endpoint.

### <a href="about:blank#_configuring_the_client"></a> Configuring the client

#### <a href="about:blank#_refresh_token_environment_variable"></a> Refresh token: Environment variable

Set `AKKA_TOKEN` before starting your application. Optionally set `AKKA_API_HOST` to override the federation plane host (defaults to `api.kalix.io:443`).

```command
export AKKA_TOKEN=<your-refresh-token>
```

```java
AkkaPlatformSdk sdk = AkkaPlatformSdk.create();
```

#### <a href="about:blank#_refresh_token_automatic_fallback_to_the_akka_cli"></a> Refresh token: Automatic fallback to the Akka CLI

If `AKKA_TOKEN` is not set and the `akka` CLI is installed and logged in, the SDK runs `akka config get refresh-token` (and `akka config get api-server-host` for non-default installations) automatically. No code changes are needed.

#### <a href="about:blank#_refresh_token_programmatic"></a> Refresh token: Programmatic

```java
AkkaPlatformSdk sdk = AkkaPlatformSdk.create(
    AkkaPlatformSdkConfig.withRefreshToken("your-refresh-token"));
```
To also override the federation plane host:

```java
AkkaPlatformSdk sdk = AkkaPlatformSdk.create(
    AkkaPlatformSdkConfig.withRefreshToken("your-refresh-token", "api.kalix.io:443"));
```

#### <a href="about:blank#_oauth_token_exchange_environment_variables"></a> OAuth token exchange: Environment variables

Set `AKKA_OAUTH_TOKEN` (or `AKKA_OAUTH_TOKEN_FILE` to point to a file containing the token) and `AKKA_OAUTH_TOKEN_AUDIENCE`. When either OAuth variable is present, it takes precedence over `AKKA_TOKEN`. If the audience variable is missing, the SDK throws an exception at startup.

```command
export AKKA_OAUTH_TOKEN="$(cat /var/run/secrets/token)"   # or a static value
export AKKA_OAUTH_TOKEN_AUDIENCE="regions/gcp-us-east1"
# -- or --
export AKKA_OAUTH_TOKEN_FILE=/var/run/secrets/token
export AKKA_OAUTH_TOKEN_AUDIENCE="regions/gcp-us-east1"
```

```java
AkkaPlatformSdk sdk = AkkaPlatformSdk.create();  // picks up the env vars automatically
```
When `AKKA_OAUTH_TOKEN_FILE` is used, the file is re-read on every token exchange, so rotating the file contents is sufficient to rotate the credential without restarting the application.

|  | When [Akka platform workload identity](services/workload-identity.html#authenticating-with-the-akka-platform-api) is configured for an Akka service, these environment variables are injected automatically into the service at runtime. Calling `AkkaPlatformSdk.create()` with no further configuration is sufficient. |

#### <a href="about:blank#_oauth_token_exchange_programmatic"></a> OAuth token exchange: Programmatic

```java
// Fixed token value
AkkaPlatformSdk sdk = AkkaPlatformSdk.create(
    AkkaPlatformSdkConfig.withOAuthToken(myOidcToken, "regions/gcp-us-east1"));

// File-based (re-read on every exchange)
AkkaPlatformSdk sdk = AkkaPlatformSdk.create(
    AkkaPlatformSdkConfig.withOAuthTokenFile("/var/run/secrets/token", "regions/gcp-us-east1"));
```

#### <a href="about:blank#_custom_token_fetcher"></a> Custom token fetcher

For authentication mechanisms not covered above, implement both `TokenConfig` and `TokenFetcher` on the same class. `TokenFetcher.getToken()` must return a `CompletableFuture<TokenWithExpiry>` containing a bearer token and its expiry instant. The SDK caches the token until 60 seconds before expiry, then calls `getToken()` again.

```java
public class MyTokenConfig implements TokenConfig, TokenFetcher {
    @Override
    public CompletableFuture<TokenWithExpiry> getToken() {
        return fetchMyToken().thenApply(t ->
            new TokenWithExpiry(t.value(), t.expiresAt()));
    }
}

AkkaPlatformSdk sdk = AkkaPlatformSdk.create(
    AkkaPlatformSdkConfig.of(new MyTokenConfig(), "api.kalix.io:443"));
```

### <a href="about:blank#_looking_up_a_project"></a> Looking up a project

Projects are identified by a UUID internally. Use `resolveProjectId` to translate a friendly name to the UUID required by the control plane API:

```java
sdk.resolveProjectId("my-project")
    .thenAccept(projectId -> System.out.println("Project ID: " + projectId));
```
To list all projects directly:

```java
sdk.projects()
    .listProjects(ListProjectsRequest.newBuilder().build())
    .thenAccept(response ->
        response.getProjectsList().forEach(p ->
            System.out.println(p.getFriendlyName() + " → " + p.getName())));
```

### <a href="about:blank#_getting_a_service"></a> Getting a service

The control plane API is accessed via `sdk.controlPlaneApi()`. Each call is automatically routed to the project’s primary region: the project UUID is parsed from the request URI, the region list is fetched and cached, and the request is forwarded to the correct regional endpoint.

```java
AkkaControlPlaneApi api = sdk.controlPlaneApi();

sdk.resolveProjectId("my-project").thenCompose(projectId ->
    api.getService("my-service", projectId)
).thenAccept(service ->
    System.out.println(service.getMetadata().getName()
        + " — " + service.getStatus()));
```
To list all services in a project:

```java
sdk.resolveProjectId("my-project").thenCompose(projectId ->
    api.listServices(projectId, null)
).thenAccept(list ->
    list.getItems().forEach(s -> System.out.println(s.getMetadata().getName())));
```

### <a href="about:blank#_creating_a_service"></a> Creating a service

```java
import io.akka.platformapi.controlplane.model.*;

Service service = new Service()
    .metadata(new ObjectMeta()
        .name("my-service"))
    .spec(new ServiceSpec()
        .containers(List.of(new ServiceSpecContainer()
            .name("main")
            .image("my-registry.example.com/my-image:1.0.0"))));

sdk.resolveProjectId("my-project").thenCompose(projectId ->
    api.createService(projectId, service)
).thenAccept(created ->
    System.out.println("Created: " + created.getMetadata().getName()));
```

### <a href="about:blank#_explicit_region_routing"></a> Explicit region routing

By default, `controlPlaneApi()` routes to the primary region. To target a specific region, for example for multi-region projects:

```java
// Region name format: "<region-id>" e.g. "gcp-us-east1"
AkkaControlPlaneApi regionalApi = sdk.controlPlaneApiForRegion("gcp-us-east1");
```
If the specified region is not associated with the project identified in the request URI, the call fails asynchronously with `IllegalArgumentException`.

Region information is cached. After a region configuration change, call `sdk.clearRegionCache()` to force a fresh lookup on the next request.

### <a href="about:blank#_closing_the_client"></a> Closing the client

Close the SDK when your application shuts down, to release the underlying gRPC channels and, if applicable, the managed `ActorSystem`:

```java
sdk.close();
```

## <a href="about:blank#_using_the_schemas_directly"></a> Using the schemas directly

The federation plane protobuf schemas are in <a href="https://github.com/akka/platform-api/tree/main/schemas/federation-plane/protobuf">`schemas/federation-plane/protobuf/`</a> and the control plane OpenAPI schema is at <a href="https://github.com/akka/platform-api/blob/main/schemas/control-plane/openapi/api.json">`schemas/control-plane/openapi/api.json`</a>, both in the `akka/platform-api` repository. This section describes the authentication and region discovery flow for clients that use these schemas without the Java client.

### <a href="about:blank#_authenticating"></a> Authenticating

All requests require a short-lived access token. Exchange your refresh token for one by calling `CreateAccessToken` on the Auth service (`api.kalix.io:443`, TLS):

Service:  kalix.api.auth.v1alpha.Auth
Method:   CreateAccessToken
Request:  CreateAccessTokenRequest {}   (empty body)
Header:   Authorization: Bearer <refresh-token> The response `AccessToken.token` is your access token. Send it as `Authorization: Bearer <access-token>` on all subsequent requests. Tokens are short-lived: cache them and refresh before `expire_time`.

To authenticate an Akka service that calls the Platform API using its own identity, instead of a stored refresh token, configure [Akka platform workload identity](services/workload-identity.html#authenticating-with-the-akka-platform-api) on the service.

### <a href="about:blank#_region_endpoint_discovery"></a> Region endpoint discovery

Control plane REST requests must be directed to the region-specific endpoint for the project, not to `api.kalix.io`. Discover the endpoint by calling `ListProjects` on the Projects service, authenticated with your access token:

Service:  kalix.api.projects.v1alpha.Projects
Method:   ListProjects
Request:  ListProjectsRequest {}
Header:   Authorization: Bearer <access-token> Each `Project` in the response contains a `regions` list. Each `Region` has:

`endpoint` The base URL of the control plane REST requests, for example `https://api.gcp-us-east1.akka.io`.

`primary` `true` for the region that should be used by default.

`name` The fully-qualified resource name, for example `projects/<id>/regions/gcp-us-east1`.

In most cases, use the region where `primary == true`. Use its `endpoint` as the base URL of all control plane REST calls for that project:

GET https://api.gcp-us-east1.akka.io/apis/kalix.io/v1alpha1/namespaces/<project-id>/kalixservices
Authorization: Bearer <access-token> If you already have the project ID, you can also call `ListRegions` directly with `parent = "projects/<project-id>"`, instead of iterating through `ListProjects`.

Page through `ListProjects` using the `next_page_token` field in the response until it is empty, since projects are returned in pages.

## <a href="about:blank#_see_also"></a> See also

- <a href="https://github.com/akka/platform-api">`akka/platform-api`</a> on GitHub, for the full schemas, the Java client source, and release notes
- [Workload Identity](services/workload-identity.html)
- [Using the Akka CLI](cli/using-cli.html)

<!-- <footer> -->
<!-- <nav> -->
[Scanning vulnerabilities](integrating-cicd/scanning-dependencies.html) [CLI](cli/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->