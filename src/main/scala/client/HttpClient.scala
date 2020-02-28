package orco.httpClient

import cats.implicits._
import io.circe.{ Decoder }
import org.http4s.{ Headers }
import org.http4s.Method.GET
import org.http4s.Status.Successful
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString
import org.http4s._
import zio._
import zio.console.Console
import zio.interop.catz._

import orco.config.Config

object dsl extends Http4sClientDsl[Task]
import dsl._

case class HttpClientResult[T](result: T, headers: Headers)

case class HttpClientError(uri: Uri, msg: String) extends Throwable

trait HttpClient {
  val httpClient: HttpClient.Service[Any]
}

object HttpClient {
  trait Service[R] {

    def get[T](uri: Uri)(implicit decoder: Decoder[T]): RIO[R, HttpClientResult[T]]
  }

  trait Live extends HttpClient {
    def client: Client[Task]
    def config: Config.Service[Any]
    def console: Console.Service[Any]

    implicit def entityDecoder[A](implicit decoder: Decoder[A]): EntityDecoder[Task, A] = jsonOf[Task, A]

    final val httpClient = new Service[Any] {
      def get[T](uri: Uri)(implicit decoder: Decoder[T]): Task[HttpClientResult[T]] = {
        val req: Task[Request[Task]] =
          config.load.flatMap(_.token match {
            case Some(token) =>
              GET(
                uri,
                Authorization(Credentials.Token(AuthScheme.Bearer, token)),
                Accept(MediaType.application.json)
              )
            case None =>
              GET(uri, Accept(MediaType.application.json))
          })

        console.putStrLn(uri.toString()) *>
          client.fetch[HttpClientResult[T]](req) {
            case Successful(resp) =>
              entityDecoder(decoder)
                .decode(resp, strict = false)
                .leftWiden[Throwable]
                .rethrowT
                .map(res => HttpClientResult(res, resp.headers)) <*
                console.putStrLn(resp.headers.get(CaseInsensitiveString("X-RateLimit-Remaining")).toString())

            case failedResponse =>
              Task.fail(HttpClientError(uri, failedResponse.status.reason))
          }
      }
    }
  }
}
