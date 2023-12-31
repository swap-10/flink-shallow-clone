/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.expressions

import org.apache.flink.table.api._
import org.apache.flink.table.planner.expressions.utils.ExpressionTestBase
import org.apache.flink.table.types.DataType
import org.apache.flink.types.Row

import org.junit.jupiter.api.Test

class DecimalTypeTest extends ExpressionTestBase {

  @Test
  def testDecimalLiterals(): Unit = {
    // implicit double
    testAllApis(11.2, "11.2", "11.2")

    // implicit double
    testAllApis(0.7623533651719233, "0.7623533651719233", "0.7623533651719233")

    // explicit decimal (with precision of 19)
    testAllApis(BigDecimal("1234567891234567891"), "1234567891234567891", "1234567891234567891")

    // explicit decimal (high precision, not SQL compliant)
    testTableApi(BigDecimal("123456789123456789123456789"), "123456789123456789123456789")

    // explicit decimal (high precision, not SQL compliant)
    testTableApi(BigDecimal("12.3456789123456789123456789"), "12.3456789123456789123456789")
  }

  @Test
  def testDecimalBorders(): Unit = {
    testAllApis(Double.MaxValue, Double.MaxValue.toString, Double.MaxValue.toString)

    testAllApis(Double.MinValue, Double.MinValue.toString, Double.MinValue.toString)
  }

  @Test
  def testDecimalArithmetic(): Unit = {

    // note: calcite type inference:
    // Decimal+ExactNumeric => Decimal
    // Decimal+Double => Double.

    // implicit cast to decimal
    testAllApis('f1 + 12, "f1 + 12", "123456789123456789123456801")

    // implicit cast to decimal
    testAllApis(lit(12) + 'f1, "12 + f1", "123456789123456789123456801")

    testAllApis(
      'f1 + BigDecimal("12.3"),
      "f1 + 12.3",
      "123456789123456789123456801.3"
    )

    testAllApis(
      lit(BigDecimal("12.3").bigDecimal) + 'f1,
      "12.3 + f1",
      "123456789123456789123456801.3")

    testAllApis('f1 + 'f1, "f1 + f1", "246913578246913578246913578")

    testAllApis('f1 - 'f1, "f1 - f1", "0")

    // exceeds max precision 38.
    //      'f1 * 'f1,
    //      "f1 * f1",
    //      "f1 * f1",
    //      "15241578780673678546105778281054720515622620750190521")

    testAllApis('f1 / 'f1, "f1 / f1", "1.00000000")
    // Decimal(30,0) / Decimal(30, 0) => Decimal(61,31) => Decimal(38,8)

    testAllApis('f1 % 'f1, "MOD(f1, f1)", "0")

    testAllApis(-'f0, "-f0", "-123456789.123456789123456789")
  }

  @Test
  def testDecimalComparison(): Unit = {
    testAllApis('f1 < 12, "f1 < 12", "FALSE")

    testAllApis('f1 > 12, "f1 > 12", "TRUE")

    testAllApis('f1 === 12, "f1 = 12", "FALSE")

    testAllApis('f5 === 0, "f5 = 0", "TRUE")

    testAllApis(
      'f1 === BigDecimal("123456789123456789123456789"),
      "f1 = CAST('123456789123456789123456789' AS DECIMAL(30, 0))",
      "TRUE")

    testAllApis(
      'f1 !== BigDecimal("123456789123456789123456789"),
      "f1 <> CAST('123456789123456789123456789' AS DECIMAL(30, 0))",
      "FALSE")

    testAllApis('f4 < 'f0, "f4 < f0", "TRUE")

    testAllApis(12.toExpr < 'f1, "12 < f1", "TRUE")

    testAllApis(12.toExpr > 'f1, "12 > f1", "FALSE")

    testAllApis(12.toExpr - 'f37, "12 - f37", "10")

    testAllApis(12.toExpr + 'f37, "12 + f37", "14")

    testAllApis(12.toExpr * 'f37, "12 * f37", "24")

    testAllApis(12.toExpr / 'f37, "12 / f37", "6")
  }

  @Test
  def testFieldAccess(): Unit = {

    // the most basic case
    testAllApis('f6, "f6", "123")

    testAllApis('f7, "f7", "123.45")

    // data from source are rounded to their declared scale before entering next step
    testAllApis('f8, "f8", "100.00")

    testAllApis('f8 + 'f8, "f8 + f8", "200.00")

    // trailing zeros are padded to the scale
    testAllApis('f9, "f9", "100.10")

    testAllApis('f9 + 'f9, "f9 + f9", "200.20")

    // source data is within precision after rounding
    testAllApis('f10, "f10", "100.00")

    testAllApis('f10 + 'f10, "f10 + f10", "200.00")

    // source data overflows over precision (after rounding)
    testAllApis('f11, "f11", "NULL")

    testAllApis('f12, "f12", "NULL")
  }

  @Test
  def testUnaryPlusMinus(): Unit = {

    testAllApis(+'f6, "+f6", "123")

    testAllApis(-'f7, "-f7", "-123.45")

    testAllApis(-((+'f6) - (-'f7)), "- (( + f6) - ( - f7))", "-246.45")
  }

  @Test
  def testPlusMinus(): Unit = {

    // see calcite ReturnTypes.DECIMAL_SUM
    // s = max(s1,s2), p-s = max(p1-s1, p2-s2) + 1
    // p then is capped at 38
    testAllApis('f13 + 'f14, "f13 + f14", "300.2434")

    testAllApis('f13 - 'f14, "f13 - f14", "-100.0034")

    // INT => DECIMAL(10,0)
    // approximate + exact => approximate
    testAllApis('f7 + 'f2, "f7 + f2", "165.45")

    testAllApis('f2 + 'f7, "f2 + f7", "165.45")

    testAllApis('f7 + 'f3, "f7 + f3", "127.65")

    testAllApis('f3 + 'f7, "f3 + f7", "127.65")

    // T-SQL -- scale may be reduced to keep the integral part. approximation may occur
    //   (38,10)+(38,28)=>(57,28)=>(38,9)
    testAllApis('f15 + 'f16, "f15 + f16", "300.024691358")

    testAllApis('f15 - 'f16, "f15 - f16", "-100.000000000")

    // 10 digits integral part
    testAllApis('f17 + 'f18, "f17 + f18", "10000000000.000000000")

    testAllApis('f17 - 'f18, "f17 - f18", "10000000000.000000000")

    // requires 39 digits
    testAllApis('f19 + 'f19, "f19 + f19", "NULL")

    // overflows in subexpression
    testAllApis('f19 + 'f19 - 'f19, "f19 + f19 - f19", "NULL")
  }

  @Test
  def testMultiply(): Unit = {

    // see calcite ReturnTypes.DECIMAL_PRODUCT
    // s = s1+s2, p = p1+p2
    // both p&s are capped at 38
    testAllApis('f20 * 'f20, "f20 * f20", "1.0000")

    testAllApis('f20 * 'f21, "f20 * f21", "2.000000")

    // INT => DECIMAL(10,0)
    // approximate * exact => approximate
    testAllApis('f20 * 'f22, "f20 * f22", "200.00")

    testAllApis('f22 * 'f20, "f22 * f20", "200.00")

    testAllApis('f20 * 'f23, "f20 * f23", "3.14")

    testAllApis('f23 * 'f20, "f23 * f20", "3.14")

    // (60,12) => (38,6), minimum scale 6 is preserved
    // while sacrificing scale for more space for integral part
    testAllApis('f24 * 'f24, "f24 * f24", "1.000000")

    testAllApis('f24 * 'f25, "f24 * f25", "2.000000")

    testAllApis(
      'f26 * 'f26,
      "f26 * f26",
      "0.00010000000000000"
    )

    // (76, 20) -> (38, 6), 0.0000006 is rounding to 0.000001
    // refer "https://blogs.msdn.microsoft.com/sqlprogrammability/
    // 2006/03/29/multiplication-and-division-with-numerics/"
    testAllApis(
      'f27 * 'f28,
      "f27 * f28",
      "0.000001"
    )

    // result overflow
    testAllApis(
      'f29 * 'f29,
      "f29 * f29",
      "NULL"
    )

    // (60,40) => (38,17), scale part is reduced to make more space for integral part
    testAllApis(
      'f30 * 'f30,
      "f30 * f30",
      "1.00000000000000000"
    )
  }

  @Test
  def testDivide(): Unit = {

    // the default impl of Calcite apparently borrows from T-SQL, but differs in details.
    // Flink overrides it to follow T-SQL exactly. See FlinkTypeFactory.createDecimalQuotient()
    testAllApis('f31 / 'f32, "f31 / f32", "0.333333")

    testAllApis('f31 / 'f33, "f31 / f33", "0.3333333")

    testAllApis('f31 / 'f34, "f31 / f34", "0.3333333333")

    testAllApis('f31 / 'f35, "f31 / f35", "0.333333")

    // INT => DECIMAL(10,0)
    // approximate / exact => approximate
    testAllApis('f36 / 'f37, "f36 / f37", "0.5000000000000")

    testAllApis('f37 / 'f36, "f37 / f36", "2.00000000000")

    testAllApis('f36 / 'f38, "f36 / f38", (1.0 / 3.0).toString)

    testAllApis('f38 / 'f36, "f38 / f36", (3.0 / 1.0).toString)

    // result overflow, because result type integral part is reduced
    testAllApis('f39 / 'f40, "f39 / f40", "NULL")
  }

  @Test
  def testMod(): Unit = {
    // MOD(Exact1, Exact2) => Exact2
    testAllApis('f41 % 'f42, "mod(f41, f42)", "3.0000")

    testAllApis('f42 % 'f41, "mod(f42, f41)", "2.0000")

    testAllApis('f41 % 'f43, "mod(f41, f43)", "3.00")

    testAllApis('f43 % 'f41, "mod(f43, f41)", "1.00")

    // signs. consistent with Java's % operator.
    testAllApis('f44 % 'f45, "mod(f44, f45)", (3 % 5).toString)

    testAllApis(-'f44 % 'f45, "mod(-f44, f45)", ((-3) % 5).toString)

    testAllApis('f44 % -'f45, "mod(f44, -f45)", (3 % (-5)).toString)

    testAllApis(-'f44 % -'f45, "mod(-f44, -f45)", ((-3) % (-5)).toString)

    // rounding in case s1>s2. note that SQL2003 requires s1=s2=0.
    // (In T-SQL, s2 is expanded to s1, so that there's no rounding.)
    testAllApis('f46 % 'f47, "mod(f46, f47)", "3.1234")
  }

  @Test // functions that treat Decimal as exact value
  def testExactionFunctions(): Unit = {

    testAllApis(ifThenElse('f48 > 'f49, 'f48, 'f49), "if(f48 > f49, f48, f49)", "3.14")

    testAllApis(
      'f48.abs(),
      "abs(f48)",
      "3.14"
    )

    testAllApis(
      (-'f48).abs(),
      "abs(-f48)",
      "3.14"
    )

    testAllApis(
      'f48.floor(),
      "floor(f48)",
      "3"
    )

    testAllApis(
      'f48.ceil(),
      "ceil(f48)",
      "4"
    )

    // calcite: SIGN(Decimal(p,s))=>Decimal(p,s)
    testAllApis(
      'f48.sign(),
      "sign(f48)",
      "1.00"
    )

    testAllApis(
      (-'f48).sign(),
      "sign(-f48)",
      "-1.00"
    )
    testAllApis(
      ('f48 - 'f48).sign(),
      "sign(f48 - f48)",
      "0.00"
    )

    // ROUND(Decimal(p,s)[,INT])
    testAllApis('f50.round(0), "round(f50)", "647")

    testAllApis('f50.round(0), "round(f50,0)", "647")

    testAllApis('f50.round(1), "round(f50,1)", "646.6")

    testAllApis('f50.round(2), "round(f50,2)", "646.65")

    testAllApis('f50.round(3), "round(f50,3)", "646.646")

    testAllApis('f50.round(4), "round(f50,4)", "646.646")

    testAllApis('f50.round(-1), "round(f50,-1)", "650")

    testAllApis('f50.round(-2), "round(f50,-2)", "600")

    testAllApis('f50.round(-3), "round(f50,-3)", "1000")

    testAllApis('f50.round(-4), "round(f50,-4)", "0")

    testAllApis('f51.round(1), "round(f51,1)", "100.0")

    testAllApis((-'f51).round(1), "round(-f51,1)", "-100.0")

    testAllApis('f51.round(-1), "round(f51,-1)", "100")

    testAllApis((-'f51).round(-1), "round(-f51,-1)", "-100")

    testAllApis('f52.round(-1), "round(f52,-1)", "NULL")
  }

  @Test // functions e.g. sin() that treat Decimal as double
  def testApproximateFunctions(): Unit = {
    // skip moving ApproximateFunctions tests from
    // sql/DecimalITCase.scala and table/DecimalITcase.scala
    // because these tests will run fail until FLINK-14036 is fixed
  }

  @Test
  def testCaseWhen(): Unit = {

    // result type: SQL2003 $9.23, calcite RelDataTypeFactory.leastRestrictive()
    testSqlApi("case f53 when 0 then f53 else f54 end", "0.0100")

    testSqlApi("case f53 when 0 then f53 else f2 end", "42.0000")

    testSqlApi("case f53 when 0 then f23 else f53 end", BigDecimal("0.0001").doubleValue().toString)
  }

  @Test
  def testEquality(): Unit = {

    // expressions that test equality.
    //   =, CASE, NULLIF, IN, IS DISTINCT FROM
    testSqlApi("f63=f64", "TRUE")

    testSqlApi("f63=f65", "TRUE")

    testSqlApi("f63=f66", "TRUE")

    testSqlApi("f64=f63", "TRUE")

    testSqlApi("f65=f63", "TRUE")

    testSqlApi("f66=f63", "TRUE")

    testSqlApi("f63 IN(f64)", "TRUE")

    testSqlApi("f63 IN(f65)", "TRUE")

    testSqlApi("f63 IN(f66)", "TRUE")

    testSqlApi("f64 IN(f63)", "TRUE")

    testSqlApi("f65 IN(f63)", "TRUE")

    testSqlApi("f66 IN(f63)", "TRUE")

    testSqlApi("f63 IS DISTINCT FROM f64", "FALSE")

    testSqlApi("f64 IS DISTINCT FROM f63", "FALSE")

    testSqlApi("f63 IS DISTINCT FROM f65", "FALSE")

    testSqlApi("f65 IS DISTINCT FROM f63", "FALSE")

    testSqlApi("f63 IS DISTINCT FROM f66", "FALSE")

    testSqlApi("f66 IS DISTINCT FROM f63", "FALSE")

    testSqlApi(
      "NULLIF(f63,f64)",
      "NULL"
    )

    testSqlApi(
      "NULLIF(f63,f65)",
      "NULL"
    )

    testSqlApi(
      "NULLIF(f63,f66)",
      "NULL"
    )

    testSqlApi(
      "NULLIF(f64,f63)",
      "NULL"
    )

    testSqlApi(
      "NULLIF(f65,f63)",
      "NULL"
    )

    testSqlApi(
      "NULLIF(f66,f63)",
      "NULL"
    )

    testSqlApi(
      "NULLIF(f63,f64)",
      "NULL"
    )

    testSqlApi(
      "case f63 when f64 then 1 else 0 end",
      "1"
    )

    testSqlApi(
      "case f63 when f65 then 1 else 0 end",
      "1"
    )

    testSqlApi(
      "case f63 when f66 then 1 else 0 end",
      "1"
    )

    testSqlApi(
      "case f64 when f63 then 1 else 0 end",
      "1"
    )

    testSqlApi(
      "case f65 when f64 then 1 else 0 end",
      "1"
    )

    testSqlApi(
      "case f66 when f65 then 1 else 0 end",
      "1"
    )
  }

  @Test
  def testComparison(): Unit = {
    testSqlApi("f63 < f64", "FALSE")

    testSqlApi("f63 < f65", "FALSE")

    testSqlApi("f63 < f66", "FALSE")

    testSqlApi("f64 < f63", "FALSE")

    testSqlApi("f65 < f63", "FALSE")

    testSqlApi("f66 < f63", "FALSE")

    // no overflow during type conversion.
    // conceptually both operands are promoted to infinite precision before comparison.
    testSqlApi("f67 < f68", "TRUE")

    testSqlApi("f67 < f69", "TRUE")

    testSqlApi("f67 < f70", "TRUE")

    testSqlApi("f68 < f67", "FALSE")

    testSqlApi("f69 < f67", "FALSE")

    testSqlApi("f70 < f67", "FALSE")

    testSqlApi("f63 between f64 and 1", "TRUE")

    testSqlApi("f64 between f63 and 1", "TRUE")

    testSqlApi("f63 between f65 and 1", "TRUE")

    testSqlApi("f65 between f63 and 1", "TRUE")

    testSqlApi("f63 between f66 and 1", "TRUE")

    testSqlApi("f66 between f63 and 1", "TRUE")

    testSqlApi("f63 between 0 and f64", "TRUE")

    testSqlApi("f64 between 0 and f63", "TRUE")

    testSqlApi("f63 between 0 and f65", "TRUE")

    testSqlApi("f65 between 0 and f63", "TRUE")

    testSqlApi("f63 between 0 and f66", "TRUE")

    testSqlApi("f66 between 0 and f63", "TRUE")
  }

  @Test
  def testCompareDecimalColWithNull(): Unit = {
    testSqlApi("f35>cast(1234567890123.123 as decimal(20,16))", "NULL")
  }

  // ----------------------------------------------------------------------------------------------

  override def testData: Row = {
    val testData = new Row(71)
    testData.setField(0, BigDecimal("123456789.123456789123456789").bigDecimal)
    testData.setField(1, BigDecimal("123456789123456789123456789").bigDecimal)
    testData.setField(2, 42)
    testData.setField(3, 4.2)
    testData.setField(4, BigDecimal("123456789").bigDecimal)
    testData.setField(5, BigDecimal("0.000").bigDecimal)
    testData.setField(6, BigDecimal("123").bigDecimal)
    testData.setField(7, BigDecimal("123.45").bigDecimal)
    testData.setField(8, BigDecimal("100.004").bigDecimal)
    testData.setField(9, BigDecimal("100.1").bigDecimal)
    testData.setField(10, BigDecimal("100.0040").bigDecimal)
    testData.setField(11, BigDecimal("123").bigDecimal)
    testData.setField(12, BigDecimal("123.0000").bigDecimal)
    testData.setField(13, BigDecimal("100.12").bigDecimal)
    testData.setField(14, BigDecimal("200.1234").bigDecimal)
    testData.setField(15, BigDecimal("100.0123456789").bigDecimal)
    testData.setField(16, BigDecimal("200.0123456789012345678901234567").bigDecimal)
    testData.setField(17, BigDecimal("1e10").bigDecimal)
    testData.setField(18, BigDecimal("0").bigDecimal)
    testData.setField(19, BigDecimal("5e37").bigDecimal)
    testData.setField(20, BigDecimal("1.00").bigDecimal)
    testData.setField(21, BigDecimal("2.0000").bigDecimal)
    testData.setField(22, 200)
    testData.setField(23, 3.14)
    testData.setField(24, BigDecimal("1").bigDecimal)
    testData.setField(25, BigDecimal("2").bigDecimal)
    testData.setField(26, BigDecimal("0.01").bigDecimal)
    testData.setField(27, BigDecimal("0.0000006").bigDecimal)
    testData.setField(28, BigDecimal("1.0").bigDecimal)
    testData.setField(29, BigDecimal("1e19").bigDecimal)
    testData.setField(30, BigDecimal("1.0").bigDecimal)
    testData.setField(31, BigDecimal("1.00").bigDecimal)
    testData.setField(32, BigDecimal("3").bigDecimal)
    testData.setField(33, BigDecimal("3").bigDecimal)
    testData.setField(34, BigDecimal("3").bigDecimal)
    testData.setField(35, BigDecimal("3").bigDecimal)
    testData.setField(36, BigDecimal("1.00").bigDecimal)
    testData.setField(37, 2)
    testData.setField(38, 3.0)
    testData.setField(39, BigDecimal("1e20").bigDecimal)
    testData.setField(40, BigDecimal("1e-15").bigDecimal)
    testData.setField(41, BigDecimal("3.00").bigDecimal)
    testData.setField(42, BigDecimal("5.00").bigDecimal)
    testData.setField(43, 7)
    testData.setField(44, BigDecimal("3").bigDecimal)
    testData.setField(45, BigDecimal("5").bigDecimal)
    testData.setField(46, BigDecimal("3.1234").bigDecimal)
    testData.setField(47, BigDecimal("5").bigDecimal)
    testData.setField(48, BigDecimal("3.14").bigDecimal)
    testData.setField(49, BigDecimal("2.17").bigDecimal)
    testData.setField(50, BigDecimal("646.646").bigDecimal)
    testData.setField(51, BigDecimal("99.99").bigDecimal)
    testData.setField(52, BigDecimal("1E38").bigDecimal.subtract(BigDecimal("1").bigDecimal))
    testData.setField(53, BigDecimal("0.0001").bigDecimal)
    testData.setField(54, BigDecimal("0.01").bigDecimal)
    testData.setField(55, "3.14")
    testData.setField(56, BigDecimal("3.15").bigDecimal)
    testData.setField(57, 3.15)
    testData.setField(58, "3.15")
    testData.setField(59, "13.14")
    testData.setField(60, BigDecimal("1.99").bigDecimal)
    testData.setField(61, "1.99")
    testData.setField(62, 1)
    testData.setField(63, BigDecimal("1").bigDecimal)
    testData.setField(64, BigDecimal("1").bigDecimal)
    testData.setField(65, 1)
    testData.setField(66, 1.0)
    testData.setField(67, BigDecimal("1").bigDecimal)
    testData.setField(68, BigDecimal("99").bigDecimal)
    testData.setField(69, 99)
    testData.setField(70, 99.0)

    testData
  }

  override def testDataType: DataType = DataTypes.ROW(
    DataTypes.FIELD("f0", DataTypes.DECIMAL(30, 18)),
    DataTypes.FIELD("f1", DataTypes.DECIMAL(30, 0)),
    DataTypes.FIELD("f2", DataTypes.INT()),
    DataTypes.FIELD("f3", DataTypes.DOUBLE()),
    DataTypes.FIELD("f4", DataTypes.DECIMAL(10, 0)),
    DataTypes.FIELD("f5", DataTypes.DECIMAL(10, 3)),
    DataTypes.FIELD("f6", DataTypes.DECIMAL(10, 0)),
    DataTypes.FIELD("f7", DataTypes.DECIMAL(7, 2)),
    DataTypes.FIELD("f8", DataTypes.DECIMAL(7, 2)),
    DataTypes.FIELD("f9", DataTypes.DECIMAL(7, 2)),
    DataTypes.FIELD("f10", DataTypes.DECIMAL(5, 2)),
    DataTypes.FIELD("f11", DataTypes.DECIMAL(2, 0)),
    DataTypes.FIELD("f12", DataTypes.DECIMAL(4, 2)),
    DataTypes.FIELD("f13", DataTypes.DECIMAL(10, 2)),
    DataTypes.FIELD("f14", DataTypes.DECIMAL(10, 4)),
    DataTypes.FIELD("f15", DataTypes.DECIMAL(38, 10)),
    DataTypes.FIELD("f16", DataTypes.DECIMAL(38, 28)),
    DataTypes.FIELD("f17", DataTypes.DECIMAL(38, 10)),
    DataTypes.FIELD("f18", DataTypes.DECIMAL(38, 28)),
    DataTypes.FIELD("f19", DataTypes.DECIMAL(38, 0)),
    DataTypes.FIELD("f20", DataTypes.DECIMAL(5, 2)),
    DataTypes.FIELD("f21", DataTypes.DECIMAL(10, 4)),
    DataTypes.FIELD("f22", DataTypes.INT()),
    DataTypes.FIELD("f23", DataTypes.DOUBLE()),
    DataTypes.FIELD("f24", DataTypes.DECIMAL(30, 6)),
    DataTypes.FIELD("f25", DataTypes.DECIMAL(30, 10)),
    DataTypes.FIELD("f26", DataTypes.DECIMAL(30, 20)),
    DataTypes.FIELD("f27", DataTypes.DECIMAL(38, 10)),
    DataTypes.FIELD("f28", DataTypes.DECIMAL(38, 10)),
    DataTypes.FIELD("f29", DataTypes.DECIMAL(38, 0)),
    DataTypes.FIELD("f30", DataTypes.DECIMAL(30, 20)),
    DataTypes.FIELD("f31", DataTypes.DECIMAL(20, 2)),
    DataTypes.FIELD("f32", DataTypes.DECIMAL(2, 1)),
    DataTypes.FIELD("f33", DataTypes.DECIMAL(4, 3)),
    DataTypes.FIELD("f34", DataTypes.DECIMAL(20, 10)),
    DataTypes.FIELD("f35", DataTypes.DECIMAL(20, 16)),
    DataTypes.FIELD("f36", DataTypes.DECIMAL(10, 2)),
    DataTypes.FIELD("f37", DataTypes.INT()),
    DataTypes.FIELD("f38", DataTypes.DOUBLE()),
    DataTypes.FIELD("f39", DataTypes.DECIMAL(30, 0)),
    DataTypes.FIELD("f40", DataTypes.DECIMAL(30, 20)),
    DataTypes.FIELD("f41", DataTypes.DECIMAL(10, 2)),
    DataTypes.FIELD("f42", DataTypes.DECIMAL(10, 4)),
    DataTypes.FIELD("f43", DataTypes.INT()),
    DataTypes.FIELD("f44", DataTypes.DECIMAL(1, 0)),
    DataTypes.FIELD("f45", DataTypes.DECIMAL(1, 0)),
    DataTypes.FIELD("f46", DataTypes.DECIMAL(10, 4)),
    DataTypes.FIELD("f47", DataTypes.DECIMAL(10, 2)),
    DataTypes.FIELD("f48", DataTypes.DECIMAL(10, 2)),
    DataTypes.FIELD("f49", DataTypes.DECIMAL(10, 2)),
    DataTypes.FIELD("f50", DataTypes.DECIMAL(10, 3)),
    DataTypes.FIELD("f51", DataTypes.DECIMAL(4, 2)),
    DataTypes.FIELD("f52", DataTypes.DECIMAL(38, 0)),
    DataTypes.FIELD("f53", DataTypes.DECIMAL(8, 4)),
    DataTypes.FIELD("f54", DataTypes.DECIMAL(10, 2)),
    DataTypes.FIELD("f55", DataTypes.STRING()),
    DataTypes.FIELD("f56", DataTypes.DECIMAL(8, 2)),
    DataTypes.FIELD("f57", DataTypes.DOUBLE()),
    DataTypes.FIELD("f58", DataTypes.STRING()),
    DataTypes.FIELD("f59", DataTypes.STRING()),
    DataTypes.FIELD("f60", DataTypes.DECIMAL(4, 2)),
    DataTypes.FIELD("f61", DataTypes.STRING()),
    DataTypes.FIELD("f62", DataTypes.INT()),
    DataTypes.FIELD("f63", DataTypes.DECIMAL(8, 2)),
    DataTypes.FIELD("f64", DataTypes.DECIMAL(8, 4)),
    DataTypes.FIELD("f65", DataTypes.INT()),
    DataTypes.FIELD("f66", DataTypes.DOUBLE()),
    DataTypes.FIELD("f67", DataTypes.DECIMAL(1, 0)),
    DataTypes.FIELD("f68", DataTypes.DECIMAL(2, 0)),
    DataTypes.FIELD("f69", DataTypes.INT()),
    DataTypes.FIELD("f70", DataTypes.DOUBLE())
  )

  override def containsLegacyTypes: Boolean = false
}
