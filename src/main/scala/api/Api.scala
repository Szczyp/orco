package orco.api

import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import zio._
import zio.interop.catz._

import orco._
import orco.httpClient.HttpClient

class Api[R <: HttpClient] {
  type ApiTask[T] = RIO[R, T]

  private val dsl = Http4sDsl[ApiTask]
  import dsl._

  case class Repo(name: String)
  case class Contributor(login: String, contributions: Int)

  val baseUri = uri"https://api.github.com"

  val service =
    HttpRoutes
      .of[ApiTask] {
        case GET -> Root / "org" / orgName / "contributors" => {
          val uri = baseUri / "orgs" / orgName / "repos"
          for {
            repos <- httpClient.get[Repo](uri)
            repoUris = repos.map(r => baseUri / "repos" / orgName / r.name / "contributors")
            contribs <- ZIO.foreachPar(repoUris)(httpClient.get[Contributor])
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
}
