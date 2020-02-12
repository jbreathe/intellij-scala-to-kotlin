package darthorimar.scalaToKotlinConverter

trait BuilderBase {
  private val builder = new StringBuffer()
  private val indentStep = 2
  private var i = 0

  def indented(block: => Unit): Unit = {
    indent()
    block
    unIndent()
  }

  def indentedIf(predicate: Boolean)(block: => Unit): Unit =
    if (predicate) indented(block)
    else block

  def rep[T](values: Seq[T], sep: String)(h: T => Unit): Unit =
    rep(values, str(sep))(h)

  def str(v: Any): Unit =
    builder.append(v)

  def rep[T](values: Seq[T], sep: => Unit)(h: T => Unit): Unit =
    if (values.nonEmpty) {
      values.init.foreach { value =>
        h(value)
        sep
      }
      h(values.last)
    }

  def repNl[T](values: Seq[T])(h: T => Unit): Unit =
    rep(values, nl())(h)

  def nl(): Unit = {
    str("\n")
    str(" " * i)
  }

  def opt[T](value: Option[T])(h: T => Unit): Unit =
    value.foreach(h)

  def text: String = builder.toString

  private def indent(): Unit = {
    i += indentStep
    nl()
  }

  private def unIndent(): Unit = {
    i -= indentStep
    nl()
  }
}
