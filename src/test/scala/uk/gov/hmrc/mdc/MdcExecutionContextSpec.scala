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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{LoggerFactory, MDC}

import java.util.concurrent.{CountDownLatch, Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

class MdcExecutionContextSpec
  extends AnyWordSpec
     with Matchers
     with LoneElement
     with Inspectors
     with BeforeAndAfter
     with ScalaFutures {

  before {
    MDC.clear()
  }

  private val ec = MdcExecutionContext()

  private val logger = LoggerFactory.getLogger(classOf[MdcExecutionContextSpec])

  "The MDC Transporting Execution Context" should {
    "capture the MDC map with values in it and put it in place when a task is run" in withCaptureOfLoggingFrom(logger){ logList =>
      MDC.setContextMap(Map("someKey" -> "something").asJava)

      Future.apply(logger.info(""))(ec).futureValue

      logList.loneElement.getMDCPropertyMap.asScala.toMap should contain("someKey" -> "something")
    }

    "ignore an null MDC map" in withCaptureOfLoggingFrom(logger){ logList =>
      MDC.setContextMap(Map.empty.asJava)

      Future.apply(logger.info(""))(ec).futureValue

      logList.loneElement.getMDCPropertyMap.asScala.toMap should be(empty)
    }

    "log values from given MDC map when multiple threads are using it concurrently by ensuring each log from each thread has been logged via MDC" in withCaptureOfLoggingFrom(logger){ logList =>
      val threadCount = 10
      val logCount    = 10

      val concurrentThreadsEc =
        ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadCount, new NamedThreadFactory("LoggerThread")))
      val startLatch      = new CountDownLatch(threadCount)
      val completionLatch = new CountDownLatch(threadCount)

      for (t <- 0 until threadCount) {
        Future {
          MDC.clear()
          startLatch.countDown()
          startLatch.await()

          for (l <- 0 until logCount) {
            MDC.setContextMap(Map("entry" -> s"${Thread.currentThread().getName}-$l").asJava)
            Future.apply(logger.info(""))(ec).futureValue
          }

          completionLatch.countDown()
        }(concurrentThreadsEc)
      }

      completionLatch.await()

      val logs = logList.map(_.getMDCPropertyMap.asScala.toMap).map(_.head._2).toSet
      logs.size should be(threadCount * logCount)

      for (t <- 1 until threadCount) {
        for (l <- 0 until logCount) {
          logs should contain(s"LoggerThread-$t-$l")
        }
      }
    }
  }

  def withCaptureOfLoggingFrom(logger: LogbackLogger)(body: (=> List[ILoggingEvent]) => Unit): Unit = {
    val appender = new ListAppender[ILoggingEvent]()
    appender.setContext(logger.getLoggerContext)
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.ALL)
    logger.setAdditive(true)
    body(appender.list.asScala.toList)
  }

  def withCaptureOfLoggingFrom(logger: org.slf4j.Logger)(body: (=> List[ILoggingEvent]) => Unit): Unit =
    withCaptureOfLoggingFrom(logger.asInstanceOf[LogbackLogger])(body)
}

case class NamedThreadFactory(name: String) extends ThreadFactory {
  val threadNo             = new AtomicInteger()
  val backingThreadFactory = Executors.defaultThreadFactory()

  def newThread(r: Runnable) = {
    val thread = backingThreadFactory.newThread(r)
    thread.setName(name + "-" + threadNo.incrementAndGet())
    thread
  }
}
