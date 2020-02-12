package darthorimar.scalaToKotlinConverter

class CollectionConverterTest extends ConverterTestBase {
  def testOptionConverters(): Unit =
    doExprTest("Some(1).map(x => x + 1).get", "1?.let { x -> x + 1 }!!")

  def testOptionCall(): Unit =
    doExprTest("Option(1)", "1")

  def testOptionGetOrElse(): Unit =
    doExprTest("Some(1).getOrElse(2)", "1 ?: 2")

  // todo: [artem] add test for ":::"
  def testListCon(): Unit =
    doExprTest("1 :: Nil", "listOf(1) + emptyList()")

  def testSeqEmpty(): Unit =
    doExprTest("""Seq.empty[Int]""", """emptyList<Int>()""")

  def testMkString(): Unit =
    doExprTest("""Seq.empty.mkString("(", ",", ")" )""", """emptyList().joinToString(",", "(", ")")""")

  def testSeqTail(): Unit =
    doExprTest("""Seq.empty.tail""", """emptyList().drop(1)""")

  def testSeqInit(): Unit =
    doExprTest("""Seq.empty.init""", """emptyList().dropLast(1)""")

  def testSeqHead(): Unit =
    doExprTest("""Seq.empty.head""", """emptyList().first()""")

  def testSeqApply(): Unit =
    doExprTest(
      """
        |val s = Seq(1,2)
        |s(0)
      """.stripMargin,
      """
        |val s: List<Int> = listOf(1, 2)
        |s[0]
      """.stripMargin
    )

  def testSeqConcat(): Unit =
    doExprTest(
      """
        |val s1 = Seq(1,2)
        |val s2 = Seq(2,3)
        |s1 ++ s2
      """.stripMargin,
      """
        |val s1: List<Int> = listOf(1, 2)
        |val s2: List<Int> = listOf(2, 3)
        |s1 + s2
      """.stripMargin
    )

  def testSeqNotEmpty(): Unit =
    doExprTest("""Seq(1,2).nonEmpty()""", """listOf(1, 2).isNotEmpty()""")

  def testSeqSize(): Unit =
    doExprTest("""Seq(1,2).size()""", """listOf(1, 2).size""")

  def testSeqOfOptionFlatten(): Unit =
    doExprTest("""Seq(Some(1),None).flatten""", """listOf(1, null).filterNotNull()""")

  def testStringRepeat(): Unit =
    doExprTest(""" "nya" * 4""", """ "nya".repeat(4)""")

  def testPairConstruct(): Unit =
    doExprTest("""1 -> 2""", """1 to 2""")

  def testPairComponents(): Unit =
    doExprTest("""(1 -> 2)._1""", """(1 to 2).first""")

  def testPairType(): Unit =
    doTest(
      """
        |def foo(a: (Int, String)) =  a
        |def bar(a: (Int, String, Char)) =  a
      """.stripMargin,
      """
        |fun foo(a: Pair<Int, String>): Pair<Int, String> = a
        |fun bar(a: Tuple3<Int, String, Char>): Tuple3<Int, String, Char> = a
      """.stripMargin
    )

  def testTryApply(): Unit =
    doTest(
      """
        |import scala.util.Try
        |val a: Try[Int] = Try(1)
      """.stripMargin,
      """
        |val a: Try<Int> = runTry { 1 }
      """.stripMargin)
}
