package com.github.agourlay.cornichon.dsl

import org.scalatest.{ Matchers, TryValues, WordSpec }
import spray.json._

class DataTableSpec extends WordSpec with Matchers with TryValues {

  "DataTable parser" must {

    "process a single line with 1 value without new line on first" in {
      val input = """ |   Name   |
                      |  "John"  |
                  """

      val expected = DataTable(
        headers = Headers(Seq("Name")),
        rows = Seq(
          Row(Seq(JsString("John")))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run().success.value should be(expected)
    }

    "process a single line with 1 value" in {
      val input = """
        |   Name   |
        |  "John"  |
        """

      val expected = DataTable(
        headers = Headers(Seq("Name")),
        rows = Seq(
          Row(Seq(JsString("John")))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run().success.value should be(expected)
    }

    "process a single line with 2 values" in {
      val input = """
        |   Name  |   Age   |
        |  "John" |   50    |
      """

      val expected = DataTable(
        headers = Headers(Seq("Name", "Age")),
        rows = Seq(
          Row(Seq(JsString("John"), JsNumber(50)))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run().success.value should be(expected)
    }

    "process multiline string" in {
      val input = """
        |  Name  |   Age  |
        | "John" |   50   |
        | "Bob"  |   11   |
      """

      val expected = DataTable(
        headers = Headers(Seq("Name", "Age")),
        rows = Seq(
          Row(Seq(JsString("John"), JsNumber(50))),
          Row(Seq(JsString("Bob"), JsNumber(11)))
        )
      )
      val p = new DataTableParser(input)
      p.dataTableRule.run().success.value should be(expected)
    }

    "notify malformed table" in {
      val input = """
        |  Name   |   Age  |
        | "John"  |   50   | "blah" |
      """

      val p = new DataTableParser(input)
      p.dataTableRule.run().failure.exception should have message "requirement failed: Datatable is malformed, all rows must have the same number of elements"
    }

    "produce valid Json Array" in {
      val input =
        """
          |  Name  |   Age  | 2LettersName |
          | "John" |   50   |    false     |
          | "Bob"  |   11   |    true      |
        """

      val p = new DataTableParser(input)
      p.dataTableRule.run().success.value.asSprayJson should be("""
        [{
          "Name":"John",
          "Age":50,
          "2LettersName": false
        },
        {
          "Name":"Bob",
          "Age":11,
          "2LettersName": true
        }]
      """.parseJson)
    }
  }
}