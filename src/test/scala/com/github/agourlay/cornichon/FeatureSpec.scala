package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.{ Feature, Scenario, Step }
import org.scalatest.{ Matchers, WordSpec }

class FeatureSpec extends WordSpec with Matchers {

  "A feature" must {
    "execute all scenarios" in {
      val steps1 = Seq(Step[Int]("first step", s ⇒ (2, s), _ > 0))
      val scenario1 = Scenario("test", steps1)

      val steps2 = Seq(
        Step[Int]("first step", s ⇒ (2, s), _ > 0), Step[Int]("second step", s ⇒ (5, s), _ > 0), Step[Int]("third step", s ⇒ (1, s), _ > 0)
      )
      val scenario2 = Scenario("test", steps2)

      val feature = new Feature {
        val featureName = "Playing with Numbers"
        val scenarios: Seq[Scenario] = Seq(scenario1, scenario2)
      }

      feature.runFeature().success should be(true)
    }

    "report failed scenario" in {
      val steps1 = Seq(Step[Int]("first step", s ⇒ (2, s), _ < 0))
      val scenario1 = Scenario("test", steps1)

      val steps2 = Seq(
        Step[Int]("first step", s ⇒ (2, s), _ > 0), Step[Int]("second step", s ⇒ (5, s), _ > 0), Step[Int]("third step", s ⇒ (1, s), _ > 0)
      )
      val scenario2 = Scenario("test", steps2)

      val feature = new Feature {
        val featureName = "Playing with Numbers"
        val scenarios: Seq[Scenario] = Seq(scenario1, scenario2)
      }

      val report = feature.runFeature()
      report.success should be(false)
    }
  }
}