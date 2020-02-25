package orco

import orco.api.Api
import orco.config._
import orco.httpClient.HttpClient
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext
import zio._
import zio.clock._
import zio.console._
import zio.interop.catz._
import cats.effect.Resource
import org.http4s.client.Client
import org.http4s.headers.{ `User-Agent`, AgentProduct }
import org.http4s.implicits._

object Main extends App {

  type AppEnv     = Clock with HttpClient
  type AppTask[T] = RIO[AppEnv, T]

  val program: Task[Unit] =
    for {
      conf <- config.load.provide(Config.Live)
      cr   <- client
      _ <- cr.use(
            c =>
              serve(conf.api)
                .provide(new Clock.Live with HttpClient.Live with Console.Live {
                  override def client = c
                  override def token  = conf.github.token
                })
          )
    } yield ()

  override def run(args: List[String]) =
    program
      .foldM(
        err => putStrLn(s"Execution failed with: $err") *> IO.succeed(1),
        _ => IO.succeed(0)
      )

  private def client: UIO[Resource[Task, Client[Task]]] =
    ZIO.runtime
      .map(
        implicit rts =>
          BlazeClientBuilder(ExecutionContext.global)
            .withUserAgent(`User-Agent`(AgentProduct("orco")))
            .withMaxTotalConnections(100)
            .resource
      )

  private def serve(conf: ApiConfig): RIO[AppEnv, Unit] = {
    val api = new Api[AppEnv]

    ZIO
      .runtime[AppEnv]
      .flatMap(
        implicit rts =>
          BlazeServerBuilder[AppTask]
            .bindHttp(conf.port, conf.endpoint)
            .withHttpApp(api.service.orNotFound)
            .serve
            .compile
            .drain
      )
  }

}
