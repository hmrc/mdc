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

import java.util.concurrent.{Executors, ExecutorService}
import scala.concurrent.{ExecutionContextExecutorService, ExecutionContextExecutor, ExecutionContext}

// based on http://yanns.github.io/blog/2014/05/04/slf4j-mapped-diagnostic-context-mdc-with-play-framework/

object MdcExecutionContext {
  def apply(): ExecutionContextExecutor =
    new MdcExecutionContext(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2)))
}

/** Manages execution to ensure that the given MDC context are set correctly
 *  in the current thread. Actual execution is performed by a delegate ExecutionContext.
 *
 *  This is mostly provided for tests, and it is expected that the ExecutionContext is injected as provided by bootstrap-play.
 */
class MdcExecutionContext(
  delegate: ExecutionContextExecutorService
) extends ExecutionContextExecutor
     with MdcExecutorService {

  override val executor = delegate

   def reportFailure(t: Throwable) =
     delegate.reportFailure(t)
}

trait MdcExecutorService {

  val executor: ExecutorService

  def execute(command: Runnable): Unit = {
    val mdcData = MDC.getCopyOfContextMap

    executor.execute { () =>
      val oldMdcData = MDC.getCopyOfContextMap
      setMdc(mdcData)
      try
        command.run()
      finally
        setMdc(oldMdcData)
    }
  }

  private def setMdc(context: java.util.Map[String, String]): Unit =
    if (context == null)
      MDC.clear()
    else
      MDC.setContextMap(context)
}
