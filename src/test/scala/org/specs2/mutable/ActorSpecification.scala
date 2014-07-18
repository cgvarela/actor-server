package org.specs2.mutable

import akka.testkit._
import akka.actor._
import akka.io.Tcp._
import scala.collection.immutable.Seq
import org.specs2._
import org.specs2.control._
import org.specs2.execute._
import org.specs2.main.ArgumentsShortcuts
import org.specs2.matcher._
import org.specs2.specification._
import org.specs2.time._

trait ActorSpecification extends SpecificationStructure
with SpecificationStringContext
with mutable.FragmentsBuilder
with mutable.SpecificationInclusion
with ArgumentsArgs
with ArgumentsShortcuts
with MustThrownMatchers
with ShouldThrownMatchers
with StandardResults
with StandardMatchResults
with mutable.Tags
with AutoExamples
with TimeConversions
with PendingUntilFixed
with Contexts
with SpecificationNavigation
with ContextsInjection
with Debug
with TestKitBase
with ImplicitSender
{
  sequential

  lazy val actorSystemName = "test-actor-system"

  implicit lazy val system = ActorSystem(actorSystemName)

  private def shutdownActorSystem() {
    TestKit.shutdownActorSystem(system)
  }

  override def map(fs: => Fragments) = super.map(fs) ^ Step(shutdownActorSystem)

  implicit def writeAsResult = new AsResult[Write] {
    def asResult(r: => Write) = success
  }

  implicit def writeSeqAsResult = new AsResult[Seq[Write]] {
    def asResult(r: => Seq[Write]) = success
  }
}
