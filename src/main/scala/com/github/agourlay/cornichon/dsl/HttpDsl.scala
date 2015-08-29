package com.github.agourlay.cornichon.dsl

import akka.http.scaladsl.model.HttpHeader
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http._
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

import scala.concurrent.duration._

trait HttpDsl extends Dsl {
  this: HttpFeature ⇒

  implicit val requestTimeout: FiniteDuration = 2000 millis

  sealed trait Request { val name: String }

  sealed trait WithoutPayload extends Request {
    def apply(url: String, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      ExecutableStep(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${params.mkString(", ")}"
      },
        action = s ⇒ {
        val x = this match {
          case GET    ⇒ Get(url, params, headers)(s)
          case DELETE ⇒ Delete(url, params, headers)(s)
        }
        x.map { case (jsonRes, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
      },
        expected = true
      )
  }

  sealed trait WithPayload extends Request {
    def apply(url: String, payload: String, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      ExecutableStep(
        title = {
        val base = s"$name to $url with payload $payload"
        if (params.isEmpty) base
        else s"$base with params ${params.mkString(", ")}"
      },
        action = s ⇒ {
        val x = this match {
          case POST ⇒ Post(payload.parseJson, url, params, headers)(s)
          case PUT  ⇒ Put(payload.parseJson, url, params, headers)(s)
        }
        x.map { case (jsonRes, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
      },
        expected = true
      )
  }

  sealed trait Streamed extends Request {
    def apply(url: String, takeWithin: FiniteDuration, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      ExecutableStep(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${params.mkString(", ")}"
      },
        action = s ⇒ {
        val x = this match {
          case GET_SSE ⇒ GetSSE(url, takeWithin, params, headers)(s)
          case GET_WS  ⇒ ???
        }
        x.map { case (source, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
      },
        expected = true
      )
  }

  case object GET extends WithoutPayload { val name = "GET" }

  case object DELETE extends WithoutPayload { val name = "DELETE" }

  case object POST extends WithPayload { val name = "POST" }

  case object PUT extends WithPayload { val name = "PUT" }

  case object GET_SSE extends Streamed { val name = "GET SSE" }

  case object GET_WS extends Streamed { val name = "GET WS" }

  def status_is(status: Int) = session_contains(LastResponseStatusKey, status.toString, Some(s"HTTP status is $status"))

  def headers_contain(headers: (String, String)*) =
    transform_assert_session(LastResponseHeadersKey, true, sessionHeaders ⇒ {
      val sessionHeadersValue = sessionHeaders.split(",")
      headers.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name:$value") }
    }, Some(s"HTTP headers contain ${headers.mkString(", ")}"))

  def response_is(jsString: String, whiteList: Boolean = false): ExecutableStep[JsValue] = {
    val jsonInput = jsString.parseJson
    transform_assert_session(LastResponseJsonKey, jsonInput, sessionValue ⇒ {
      val sessionValueJson = sessionValue.parseJson
      if (whiteList) {
        jsonInput.asJsObject.fields.map {
          case (k, v) ⇒
            val value = sessionValueJson.asJsObject.getFields(k)
            if (value.isEmpty) throw new WhileListError(s"White list error - key '$k' is not defined in object '$sessionValueJson")
            else (k, v)
        }.toJson
      } else sessionValueJson
    }, Some(s"HTTP response is $jsString with whiteList=$whiteList"))
  }

  def response_is(jsString: String, ignoredKeys: String*): ExecutableStep[JsValue] =
    transform_assert_session(LastResponseJsonKey, jsString.parseJson, sessionValue ⇒ {
      if (ignoredKeys.isEmpty) sessionValue.parseJson
      else sessionValue.parseJson.asJsObject.fields.filterKeys(!ignoredKeys.contains(_)).toJson
    }, Some(s"HTTP response is $jsString"))

  def extract_from_response(extractor: JsValue ⇒ String, target: String) =
    extract_from_session(LastResponseJsonKey, s ⇒ extractor(s.parseJson), target)

  def response_is(mapFct: JsValue ⇒ String, jsString: String) =
    transform_assert_session(LastResponseJsonKey, jsString, sessionValue ⇒ {
      mapFct(sessionValue.parseJson)
    }, Some(s"HTTP response with transformation is $jsString"))

  def show_last_status = show_session(LastResponseStatusKey)

  def show_last_response_json = show_session(LastResponseJsonKey)

  def show_last_response_headers = show_session(LastResponseHeadersKey)

  def response_array_is(expected: String, ordered: Boolean = true): ExecutableStep[Iterable[JsValue]] =
    stringToJson(expected) match {
      case expectedArray: JsArray ⇒
        if (ordered) response_array_is(_.elements, expectedArray.elements, Some(s"response array is $expected"))
        else response_array_is(s ⇒ s.elements.toSet, expectedArray.elements.toSet, Some(s"response array not ordered is $expected"))
      case _ ⇒ throw new NotAnArrayError(expected)
    }

  def response_array_is[A](mapFct: JsArray ⇒ A, expected: A, title: Option[String]): ExecutableStep[A] =
    transform_assert_session[A](LastResponseJsonKey, expected, sessionValue ⇒ {
      val sessionJSON = sessionValue.parseJson
      sessionJSON match {
        case arr: JsArray ⇒
          log.debug(s"response_body_array_is applied to ${arr.toString()}")
          mapFct(arr)
        case _ ⇒ throw new NotAnArrayError(sessionJSON.toString())
      }
    }, title)

  def response_array_size_is(size: Int) = response_array_is(_.elements.size, size, Some(s"response array size is $size"))

  def response_array_contains(element: String) = response_array_is(_.elements.contains(element.parseJson), true, Some(s"response array contains $element"))

  def response_array_does_not_contain(element: String) = response_array_is(_.elements.contains(element.parseJson), false, Some(s"response array does not contain $element"))

  private def stringToJson(input: String): JsValue =
    if (input.trim.head != '|') input.parseJson
    else DataTableParser.parseDataTable(input).asJson
}