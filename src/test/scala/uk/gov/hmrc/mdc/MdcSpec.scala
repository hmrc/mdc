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

import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.MDC

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

class MdcSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfter {

  before {
    MDC.clear()
  }

  implicit val ec: ExecutionContext =
    new MdcExecutionContext(ExecutionContext.Implicits.global.asInstanceOf[ExecutionContextExecutorService])

  "mdcData" should {
    "return a Scala Map" in {
      MDC.put("something1", "something2")
      Mdc.mdcData shouldBe Map("something1" -> "something2")
    }
  }

  "Preserving MDC" should {
    "show that MDC is lost when switching contexts" in {
      org.slf4j.MDC.put("k", "v")

      runActionWhichLosesMdc()
        .map(_ => Option(MDC.get("k")))
        .futureValue shouldBe None
    }

    "restore MDC" in {
      org.slf4j.MDC.put("k", "v")

      Mdc.preservingMdc(runActionWhichLosesMdc())
        .map(_ => Option(MDC.get("k")))
        .futureValue shouldBe Some("v")
    }

    "restore MDC when exception is thrown" in {
      org.slf4j.MDC.put("k", "v")

      Mdc.preservingMdc(runActionWhichLosesMdc(fail = true))
        .recover { case _ =>
          Option(MDC.get("k"))
        }.futureValue shouldBe Some("v")
    }
  }

  private def runActionWhichLosesMdc(fail: Boolean = false): Future[Any] = {
    val as = ActorSystem("as")
    org.apache.pekko.pattern.after(10.millis, as.scheduler)(Future(())(as.dispatcher))(as.dispatcher)
      .map(a => if (fail) sys.error("expected test exception") else a)(as.dispatcher)
  }
}
