/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mdc

import org.slf4j.MDC

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

trait Mdc {

  def mdcData: Map[String, String] =
    Option(MDC.getCopyOfContextMap).fold(Map.empty[String, String])(_.asScala.toMap)

  def withMdc[A](block: => Future[A], mdcData: Map[String, String])(implicit ec: ExecutionContext): Future[A] =
    block.map { a =>
      putMdc(mdcData)
      a
    }.recoverWith {
      case t =>
        putMdc(mdcData)
        Future.failed(t)
    }

  def putMdc(mdc: Map[String, String]): Unit =
    mdc.foreach {
      case (k, v) => MDC.put(k, v)
    }

  /** Restores MDC data to the continuation of a block, which may be discarding MDC data (e.g. uses a different execution context)
    */
  def preservingMdc[A](block: => Future[A])(implicit ec: ExecutionContext): Future[A] =
    withMdc(block, mdcData)
}

object Mdc extends Mdc

trait MdcImplicits {
  implicit class MdcFuture[A](f: => Future[A]) {
    def preservingMdc(implicit ec: ExecutionContext): Future[A] =
      Mdc.preservingMdc(f)
  }
}

object MdcImplicits extends MdcImplicits
