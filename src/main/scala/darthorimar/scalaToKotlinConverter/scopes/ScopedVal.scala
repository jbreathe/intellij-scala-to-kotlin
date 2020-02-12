package darthorimar.scalaToKotlinConverter.scopes

import darthorimar.scalaToKotlinConverter.scopes.ScopedVal.SetScopedVal

class ScopedVal[T](initial: T) {
  private var stack: List[T] = initial :: Nil

  def update(updateFunc: T => T): SetScopedVal[T] =
    set(updateFunc.apply(get))

  def set(value: T): SetScopedVal[T] =
    new SetScopedVal[T](value, this)

  def call[R](func: T => R): R =
    func.apply(get)

  def get: T = stack.head
}

object ScopedVal {
  def scoped[T](vals: SetScopedVal[_]*)(body: => T): T = {
    vals.foreach(_.set())
    try body
    finally vals.foreach(_.unset())
  }

  class SetScopedVal[T](value: T, scopedVal: ScopedVal[T]) {
    private[ScopedVal] def unset(): Unit = {
      scopedVal.stack = scopedVal.stack.tail
    }

    private[ScopedVal] def set(): Unit = {
      scopedVal.stack = value :: scopedVal.stack
    }
  }

  implicit def implicitGet[T](scopedVal: ScopedVal[T]): T =
    scopedVal.get
}
