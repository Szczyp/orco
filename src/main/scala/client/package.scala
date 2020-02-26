package orco

import io.circe.Decoder
import org.http4s.Uri
import zio._

import orco.httpClient

package object httpClient extends HttpClient.Service[HttpClient] {
  def get[T](uri: Uri)(implicit decoder: Decoder[T]) = ZIO.accessM(_.httpClient.get(uri)(decoder))
}
