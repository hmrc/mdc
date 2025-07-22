# MDC

![](https://img.shields.io/github/v/release/hmrc/mdc)

A library that provides utilties for working with Mapped Diagnostic Context (MDC).

## Adding to your build

In your SBT build add:

```scala
resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")

libraryDependencies += "uk.gov.hmrc" %% "mdc" % "x.x.x"
```

## How to use

See [Logback docs](https://logback.qos.ch/manual/mdc.html) for an explanation of Mapped Diagnostic Context (MDC).

[bootstrap-play](https://github.com/hmrc/bootstrap-play) is reponsible for initialising MDC for every new request with a Play Filter (extra MDC can be added as required). It also provides an `ExecutionContext` to preserve MDC in a multi-threaded environment. So MDC should be included in all application logs.

However, there are places where MDC can be lost, async boundaries (typically using `Promise`s).

This library provides some helpers for working with async boundaries, and can be used by libraries or services that deal with them.

For example

```scala
import uk.gov.hmrc.mdc.MDC

def step1(): Future[Unit] = ??? // a standard composed `Future`
def step2(): Future[Unit] = ??? // a composed `Future` involving an async boundary

for
  _ <- step1()
  _ =  logger.debug("after step1")
  _ <- MDC.preservingMdc(step2()) // without the `preservingMdc`, the following log would loose MDC
  _ =  logger.debug("after step2")
yield ()

// the following is the same, but using fluent style
import uk.gov.hmrc.mdc.MdcImplicits.*
for
  _ <- step1()
  _ =  logger.debug("after step1")
  _ <- step2().preservingMdc // without the `preservingMdc`, the following log would loose MDC
  _ =  logger.debug("after step2")
yield ()
```

There is also a `MdcExecutionContext` for testing code involving async boundaries. Services however should just use the injected `ExecutionContext` provided by bootstrap-play.

### Adding data to MDC

When adding to data to MDC, it should be added via the `RequestMdc`, providing the requestId the data should be correlated to.

```scala
RequestMdc.add(request.id, Map("mykey", "myvalue"))
```

This not only ensures the data is available to the current thread's MDC, it also ensures that the data can be restored to any thread where the request is in scope. It can be restored with:

```scala
RequestMdc.init(request.id)
```

Some recommended places for doing this are in a Play Action or Error Handler, since MDC added by a Play Filter is sometimes lost when being invoked by Play.

It is also recommended to call `org.slf4j.MDC.clear()` when starting a Thread without a Request (e.g. a scheduler) to ensure no data has been left behind.

Note, it is not necessary to clear the `RequestMdc` since the data is often required longer than expected (e.g. Play error handlers are called after Play filters), and the data is stored in a `WeakHashMap` so becomes a candidate for Garbage Collection when the Request is no longer referenced.


## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
