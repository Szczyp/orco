package orco.api

import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.{ Headers, HttpRoutes, Uri }
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Link
import org.http4s.implicits._
import zio._
import zio.interop.catz._

import orco._
import orco.httpClient.{ HttpClient, HttpClientError, HttpClientResult }

class Api[R <: HttpClient](connections: Int) {
  type ApiTask[T] = RIO[R, T]

  private val dsl = Http4sDsl[ApiTask]
  import dsl._

  case class Repo(name: String)
  case class Contributor(login: String, contributions: Int)

  val baseUri = uri"https://api.github.com"

  def getContributors(u: Uri) =
    httpClient
      .get[List[Contributor]](u)
      .orElse(ZIO.succeed(HttpClientResult(List.empty, Headers.empty)))

  def foreachPar[R1, E, A, B](xs: Iterable[A])(a: A => ZIO[R1, E, B]): ZIO[R1, E, List[B]] =
    ZIO.foreachParN(connections)(xs)(a)

  def pages(headers: Headers) =
    headers.get(Link) match {
      case None => List.empty
      case Some(link) =>
        val uri  = link.values.last.uri
        val last = uri.query.params("page").toInt
        (2 to last).map(page => uri.withQueryParam("page", page)).toList
    }

  val service =
    HttpRoutes
      .of[ApiTask] {
        case GET -> Root / "org" / orgName / "contributors" => {
          val uri = baseUri / "orgs" / orgName / "repos"

          val program = for {
            repos     <- httpClient.get[List[Repo]](uri)
            moreRepos <- foreachPar(pages(repos.headers))(httpClient.get[List[Repo]])
            contribs <- foreachPar(
                         (repos :: moreRepos).flatMap(
                           rs => rs.result.map(r => baseUri / "repos" / orgName / r.name / "contributors")
                         )
                       )(getContributors)
            moreContribs <- foreachPar(contribs.flatMap(cs => pages(cs.headers)))(getContributors)
            res <- Ok(
                    contribs
                      .concat(moreContribs)
                      .flatMap(_.result)
                      .groupBy(_.login)
                      .view
                      .mapValues(_.map(_.contributions).sum)
                      .toSeq
                      .map(Contributor.tupled)
                      .sortBy(_.contributions)(Ordering[Int].reverse)
                      .asJson
                  )
          } yield res

          program.catchSome {
            case HttpClientError(uri, msg) => FailedDependency(s"$uri $msg")
          }
        }
      }
}
