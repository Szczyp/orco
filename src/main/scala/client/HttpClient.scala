package orco.httpClient

import cats.implicits._
import io.circe.{ Decoder }
import org.http4s.{ Headers, Request }
import org.http4s.Method.GET
import org.http4s.Status.Successful
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{ AuthScheme, Credentials, MediaType }
import org.http4s.{ EntityDecoder, Uri }
import zio._
import zio.console.Console
import zio.interop.catz._

object dsl extends Http4sClientDsl[Task]
import dsl._

trait HttpClient {
  val httpClient: HttpClient.Service[Any]
}

object HttpClient {
  trait Service[R] {
    def get[T](uri: Uri)(implicit decoder: Decoder[List[T]]): RIO[R, List[T]]
  }

  trait Live extends HttpClient {
    def client: Client[Task]
    def token: Option[String]
    def console: Console.Service[Any]

    implicit def entityDecoder[A](implicit decoder: Decoder[A]): EntityDecoder[Task, A] = jsonOf[Task, A]

    final val httpClient = new Service[Any] {
      def get[T](uri: Uri)(implicit decoder: Decoder[List[T]]): Task[List[T]] = {
        val req = token match {
          case Some(token) =>
            GET(uri, Authorization(Credentials.Token(AuthScheme.Bearer, token)), Accept(MediaType.application.json))
          case None =>
            GET(uri, Accept(MediaType.application.json))
        }

        console.putStrLn(uri.toString()) *>
        client.fetch[List[T]](req) {
          case Successful(resp) =>
            (for {
              first <- entityDecoder(decoder)
                        .decode(resp, strict = false)
                        .leftWiden[Throwable]
                        .rethrowT
              more <- Task.collectAllPar(pages(resp.headers, req).map(r => client.expect[List[T]](r)))
             } yield (first :: more).flatten)
              .orElse(Task.succeed(List.empty)) <*
              console.putStrLn(resp.headers.get(CaseInsensitiveString("X-RateLimit-Remaining")).toString())

          case failedResponse =>
            Task.fail(new Exception(failedResponse.status.reason))
         }
      }
    }

    private def pages(headers: Headers, request: Task[Request[Task]]): List[Task[Request[Task]]] =
      headers.get(Link) match {
        case None => List.empty
        case Some(link) =>
          val last = link.values.last.uri.query.params("page").toInt
          (2 to last).map(page => request.map(r => r.withUri(r.uri.withQueryParam("page", page)))).toList
      }
  }
}
