# Ktor Server GraphQL

## About

Ktor Server GraphQL is a [ktor](https://ktor.io) plugin built on top
of [graphql-kotlin](https://github.com/ExpediaGroup/graphql-kotlin).
The plugin is meant to ease the configuration of `kotlin-graphql-server` within a ktor project and includes the
necessary libraries as dependencies.

## Installation

Add the following dependency to your `build.gradle.kts`:

```kotlin
implementation("com.bscharm:ktor-server-graphql:1.0.0")
```

## Usage

The plugin can be installed like any other ktor (`2.0 +`) plugin. Here installation is shown using ktor's
[embedded server syntax](https://ktor.io/docs/create-server.html#embedded):

```kotlin
embeddedServer(Netty, port = 8080) {
    install(GraphQL) {
        queries = listOf(SomeQuery(), SomeOtherQuery())
        mutations = listOf(SomeMutation())
        packages = listOf("my.schema.package")
    }
}.start(wait = true)
```

### Dependencies

As the underlying framework, [graphql-java](https://www.graphql-java.com/), relies
on [jackson](https://github.com/FasterXML/jackson) for serialization, so too
does this plugin. If you do not have the [ContentNegotiation](https://ktor.io/docs/serialization.html) plugin installed
it will be installed for you but _scoped to the route for GraphQL_. If you install on your own, be sure to use jackson
as the serializer.

## Configuration

| Property           | Type                     | Default Value                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|--------------------|--------------------------|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| queries            | `List<Query>`            | `emptyList()`                | List of top level queries for your GraphQL schema. Each must meet the [Query](https://github.com/ExpediaGroup/graphql-kotlin/blob/master/servers/graphql-kotlin-server/src/main/kotlin/com/expediagroup/graphql/server/operations/Query.kt) interface                                                                                                                                                                                                |
| mutations          | `List<Mutation>`         | `emptyList()`                | List of top level mutations for your GraphQL schema. Each must meet the [Mutation](https://github.com/ExpediaGroup/graphql-kotlin/blob/master/servers/graphql-kotlin-server/src/main/kotlin/com/expediagroup/graphql/server/operations/Mutation.kt) interface                                                                                                                                                                                        |
| subscriptions      | `List<Subscription>`     | `emptyList()`                | List of top level subscriptions for your GraphQL schema. Each must meet the [Subscription](https://github.com/ExpediaGroup/graphql-kotlin/blob/master/servers/graphql-kotlin-server/src/main/kotlin/com/expediagroup/graphql/server/operations/Subscription.kt) interface                                                                                                                                                                            |
| packages           | `List<String>`           | `emptyList()`                | List of packages where non-primitive types which are referenced in your schema (Queries or Mutations) are located. The schema will fail on app startup if misconfigured                                                                                                                                                                                                                                                                              |
| hooks              | `SchemaGeneratorHooks`   | `NoopSchemaGeneratorHooks`   | Custom `SchemaGeneratorHooks` used to support types beyond the supported primitives. See [the documentation](https://opensource.expediagroup.com/graphql-kotlin/docs/schema-generator/customizing-schemas/generator-config/#schemageneratorhooks) for more details. An [example to support UUID](https://opensource.expediagroup.com/graphql-kotlin/docs/schema-generator/customizing-schemas/generator-config/#schemageneratorhooks) is also given. |
| contextFactory     | `ContextFactoryFunction` | `NoopContextFactoryFunction` | Custom `ContextFactoryFunction` used to build up the context map for each request. This function takes the `ApplicationRequest` and returns a map which can be consumed by resolvers. See [the documentation](https://opensource.expediagroup.com/graphql-kotlin/docs/server/graphql-context-factory/) for details and examples.                                                                                                                     |
| playground         | `Boolean`                | `false`                      | If enabled, will include the [GraphQL Playground](https://github.com/graphql/graphql-playground) mounted at `playgroundPath`                                                                                                                                                                                                                                                                                                                         |
| authentication     | `Boolean`                | `false`                      | If enabled, will attempt to find the ktor auth plugin configuration using `authenticationName` (optional)                                                                                                                                                                                                                                                                                                                                            |
| authenticationName | `String?`                | `null`                       | Use a specific named auth configuration. If left null, will use the any unnamed auth configuration. See [ktor docs](https://ktor.io/docs/authentication.html) for more details on auth configuration                                                                                                                                                                                                                                                 |
| path               | `String`                 | `"graphql"`                  | Path where the GraphQL executor will be mounted                                                                                                                                                                                                                                                                                                                                                                                                      |
| subscriptionsPath  | `String`                 | `"subscriptions"`            | Path where the GraphQL subscription websocket executor will be mounted                                                                                                                                                                                                                                                                                                                                                                               |
| playgroundPath     | `String`                 | `"playground"`               | Path where the playground will be mounted if enabled                                                                                                                                                                                                                                                                                                                                                                                                 |

## Examples

#### Authentication

```kotlin
embeddedServer(Netty, port = 8080) {
    install(Authentication) {
        basic("custom-auth-name") {
            validate {
                if (it.name == "foo" && it.password == "bar") {
                    UserIdPrincipal("foo")
                } else {
                    null
                }
            }
        }
    }

    install(GraphQL) {
        queries = listOf(SomeQuery(), SomeOtherQuery())
        mutations = listOf(SomeMutation())
        packages = listOf("my.schema.package")
        authentication = true
        authenticationName = "custom-auth-name" // this can be omitted if omitted above
    }
}.start(wait = true)
```

#### ContextFactory

```kotlin
embeddedServer(Netty, port = 8080) {
    val factoryFunction: ContextFactoryFunction = { request: ApplicationRequest ->
        // insert value from custom header into the map
        return request.headers.get("X-CUSTOM-HEADER")?.let { mapOf("customHeader" to it) } ?: emptyMap()
    }

    install(GraphQL) {
        queries = listOf(SomeQuery(), SomeOtherQuery())
        mutations = listOf(SomeMutation())
        packages = listOf("my.schema.package")
        authentication = true
        authenticationName = "custom-auth-name" // this can be omitted if omitted above
        contextFactory = factoryFunction
    }
}.start(wait = true)
```

## Development

While the project is configured as kotlin multiplatform, currently only the JVM target is supported.

### Tests

To run tests:

```shell
./gradlew jvmTest
```

You can also run the following to compile as well as run tests:

```shell
./gradlew check
```

### Local Installation

To install the plugin to your local maven repository for testing, the `maven-publish` plugin is installed.

```shell
./gradlew publishToMavenLocal
```
