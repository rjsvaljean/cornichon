package com.github.agourlay.cornichon.core.dsl

import com.github.agourlay.cornichon.core.Feature.FeatureDef
import com.github.agourlay.cornichon.core._

import scala.language.higherKinds

trait Dsl {

  sealed trait Starters {
    def I[Step[_]](step: Step[_]): Step[_] = step
    def apply[A](title: String)(action: Session ⇒ (A, Session))(expected: A): Step[A] = Step[A](title, action, expected)
  }
  case object When extends Starters
  case object Then extends Starters {
    def assert[Step[_]](step: Step[_]): Step[_] = step
  }
  case object And extends Starters {
    def assert[Step[_]](step: Step[_]): Step[_] = step
  }
  case object Given extends Starters

  def scenario(name: String)(steps: Step[_]*): Scenario = Scenario(name, steps)

  def feature(name: String)(scenarios: Scenario*): FeatureDef = FeatureDef(name, scenarios)

  def SET(input: (String, String)): Step[Boolean] = {
    val (key, value) = input
    Step(s"add '$key'->'$value' to session",
      s ⇒ (true, s.addValue(key, value)), true)
  }

  def assertSessionWithMap[A](key: String, expected: A, mapValue: String ⇒ A) =
    Step(s"assert session '$key' against predicate",
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ mapValue(v)), s), expected)

  def assertSession[A](key: String, value: String) =
    Step(s"assert '$key' against predicate",
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v), s), value)

  def showSession =
    Step(s"show session",
      s ⇒ {
        println(s.content)
        (true, s)
      }, true)

  def showSession(key: String) =
    Step(s"show session",
      s ⇒ {
        val value = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v)
        println(value)
        (true, s)
      }, true)
}