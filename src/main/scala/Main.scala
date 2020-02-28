package orco

import cats.effect.Resource
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.{ `User-Agent`, AgentProduct }
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext
import zio._
import zio.clock._
import zio.console._
import zio.interop.catz._

import orco.api.Api
import orco.config._
import orco.httpClient.HttpClient

object Main extends App {
  type AppEnv     = Clock with HttpClient
  type AppTask[T] = RIO[AppEnv, T]

  val program: Task[Unit] =
    for {
      conf <- config.load.provide(Config.Live)
      cr   <- client(conf.connections)
      _ <- cr.use(
            c =>
              serve(conf)
                .provide(new Clock.Live with HttpClient.Live with Console.Live {
                  override def client = c
                  override def token  = conf.token
                })
          )
    } yield ()

  override def run(args: List[String]) =
    program
      .foldM(
        err => putStrLn(s"Execution failed with: $err") *> IO.succeed(1),
        _ => IO.succeed(0)
      )

  private def client(connections: Int): UIO[Resource[Task, Client[Task]]] =
    ZIO.runtime
      .map(
        implicit rts =>
          BlazeClientBuilder(ExecutionContext.global)
            .withUserAgent(`User-Agent`(AgentProduct("orco")))
            .withMaxTotalConnections(connections)
            .resource
      )

  private def serve(conf: AppConfig): RIO[AppEnv, Unit] =
    ZIO
      .runtime[AppEnv]
      .flatMap(
        implicit rts =>
          BlazeServerBuilder[AppTask]
            .bindHttp(conf.port, conf.endpoint)
            .withHttpApp(new Api[AppEnv](conf.connections).service.orNotFound)
            .serve
            .compile
            .drain
      )
}
