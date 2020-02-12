package darthorimar.scalaToKotlinConverter

// todo: [artem] return type "Unit" is unnecessary in Kotlin
class RealConverterTest extends ConverterTestBase {
  def testScopedVal(): Unit =
    doTest(
      """
        |package darthorimar.scalaToKotlinConverter.scopes
        |
        |import darthorimar.scalaToKotlinConverter.scopes.ScopedVal.SetScopedVal
        |
        |class ScopedVal[T](initial: T) {
        |  private var stack: List[T] = initial :: Nil
        |
        |  def update(updateFunc: T => T): SetScopedVal[T] =
        |    set(updateFunc.apply(get))
        |
        |  def set(value: T): SetScopedVal[T] =
        |    new SetScopedVal[T](value, this)
        |
        |  def call[R](func: T => R): R =
        |    func.apply(get)
        |
        |  def get: T = stack.head
        |}
        |
        |object ScopedVal {
        |  def scoped[T](vals: SetScopedVal[_]*)(body: => T): T = {
        |    vals.foreach(_.set())
        |    try body
        |    finally vals.foreach(_.unset())
        |  }
        |
        |  class SetScopedVal[T](value: T, scopedVal: ScopedVal[T]) {
        |    private[ScopedVal] def unset(): Unit = {
        |      scopedVal.stack = scopedVal.stack.tail
        |    }
        |
        |    private[ScopedVal] def set(): Unit = {
        |      scopedVal.stack = value :: scopedVal.stack
        |    }
        |  }
        |
        |  implicit def implicitGet[T](scopedVal: ScopedVal[T]): T =
        |    scopedVal.get
        |}
      """.stripMargin,
      """
        |package darthorimar.scalaToKotlinConverter.scopes
        |
        |open class ScopedVal<T>(private val initial: T) {
        |  private var stack: List<T> = listOf(initial) + emptyList()
        |  fun update(updateFunc: (T) -> T): SetScopedVal<T> = set(updateFunc(get()))
        |  fun set(value: T): SetScopedVal<T> = SetScopedVal<T>(value, this)
        |  fun <R> call(func: (T) -> R): R = func(get())
        |  fun get(): T = stack.first()
        |  companion object {
        |    fun <T> scoped(vararg vals: SetScopedVal<*>, body: () -> T): T {
        |      vals.forEach { it.set() }
        |      return try {
        |        body()
        |      } finally {
        |        vals.forEach { it.unset() }
        |      }
        |    }
        |    open class SetScopedVal<T>(private val value: T, private val scopedVal: ScopedVal<T>) {
        |      internal fun unset(): Unit {
        |        scopedVal.stack = scopedVal.stack.drop(1)
        |      }
        |      internal fun set(): Unit {
        |        scopedVal.stack = listOf(value) + scopedVal.stack
        |      }
        |    }
        |    fun <T> implicitGet(scopedVal: ScopedVal<T>): T = scopedVal.get()
        |  }
        |}
      """.stripMargin
    )
}
