package orco

import orco.httpClient
import zio._
import io.circe.Decoder
import org.http4s.Uri

package object httpClient extends HttpClient.Service[HttpClient] {
  def get[T](uri: Uri)(implicit decoder: Decoder[List[T]]) = ZIO.accessM(_.httpClient.get(uri)(decoder))
}
