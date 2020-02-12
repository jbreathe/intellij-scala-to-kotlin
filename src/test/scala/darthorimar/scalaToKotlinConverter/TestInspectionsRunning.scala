package darthorimar.scalaToKotlinConverter

class TestInspectionsRunning extends ConverterTestBase {
  def testExtraBracketsInspection(): Unit =
    doExprTest("""val a = ((1 + 1))""", """val a: Int = 1 + 1""")
}
