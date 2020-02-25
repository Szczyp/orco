package orco.api

import zio._
import zio.interop.catz._
import org.http4s.dsl.Http4sDsl
import org.http4s.{ HttpRoutes, Uri }
import orco._
import orco.httpClient.HttpClient
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._

class Api[R <: HttpClient] {
  type ApiTask[T] = RIO[R, T]

  private val dsl = Http4sDsl[ApiTask]
  import dsl._

  case class Repo(name: String, contributors_url: Uri)
  case class Contributor(login: String, contributions: Int)

  val service =
    HttpRoutes
      .of[ApiTask] {
        case GET -> Root / "org" / orgName / "contributors" =>
          for {
            uri      <- ZIO.fromEither(Uri.fromString(s"https://api.github.com/orgs/${orgName}/repos"))
            repos    <- httpClient.get[Repo](uri)
            contribs <- ZIO.collectAllPar(repos.map(r => httpClient.get[Contributor](r.contributors_url)))
            res <- Ok(
                    contribs.flatten
                      .groupBy(_.login)
                      .view
                      .mapValues(_.map(_.contributions).sum)
                      .toSeq
                      .map(Contributor.tupled)
                      .sortBy(_.contributions)(Ordering[Int].reverse)
                      .asJson
                  )
          } yield res
      }
}
