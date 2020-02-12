package darthorimar.scalaToKotlinConverter.step.transform

import darthorimar.scalaToKotlinConverter.Exprs
import darthorimar.scalaToKotlinConverter.Exprs.listType
import darthorimar.scalaToKotlinConverter.ast._
import darthorimar.scalaToKotlinConverter.step.transform.Helpers.ApplyCall
import darthorimar.scalaToKotlinConverter.types.TypeUtils.{KotlinList, ListType, WithType}
import darthorimar.scalaToKotlinConverter.types.{KotlinTypes, StdTypes}

class CollectionTransform extends Transform {
  override protected val action: PartialFunction[AST, AST] = {

    // Options

    // Some(x) --> x
    case ApplyCall(WithType(ScalaType("scala.Some$")), Seq(v)) =>
      transform[Expr](v)

    // None --> null
    case RefExpr(NullableType(_), None, "scala.None", _, _) =>
      Exprs.nullLit

    // opt.map(f), opt.flatMap(f) --> opt?.let {f(it)}
    case CallExpr(
    exprType,
    RefExpr(refTy, Some(referenceObject@WithType(NullableType(_))), "map" | "flatMap", typeParams, true),
    Seq(p),
    paramsExpectedTypes) =>
      ParenthesesExpr(
        CallExpr(
          transform[Type](exprType),
          RefExpr(transform[Type](refTy),
            Some(PostfixExpr(referenceObject.exprType, transform[Expr](referenceObject), "?")),
            "let",
            typeParams.map(transform[Type]),
            isFunctionRef = true),
          Seq(transform[Expr](p)),
          paramsExpectedTypes.map(transform[CallParameterInfo])
        ))

    // opt.getOrElse(x) --> opt ?: x
    case CallExpr(_,
    RefExpr(refTy, Some(referenceObject@WithType(NullableType(_))), "getOrElse" | "orElse", _, true),
    Seq(p), _) if referenceObject.exprType.isInstanceOf[NullableType] =>
      val param = p match {
        case LambdaExpr(_, _, expr, _) => expr
        case _ => p
      }
      ParenthesesExpr(
        Exprs.simpleInfix(transform[Type](refTy), "?:", transform[Expr](referenceObject), transform[Expr](param)))

    // opt.get --> opt!!
    case CallExpr(_,
    RefExpr(refTy, Some(referenceObject@WithType(NullableType(_))), "get", _, true),
    _,
    _) =>
      PostfixExpr(transform[Type](refTy), transform[Expr](referenceObject), "!!")

    // Seqs

    //Seq(1,2,3) --> listOf(1,2,3)
    case CallExpr(exprType,
    RefExpr(refTy, Some(RefExpr(_, None, "scala.Seq" | "scala.List", typeParams, false)), "apply", _, _),
    params,
    paramsExpectedTypes) =>
      CallExpr(
        transform[Type](exprType),
        RefExpr(transform[Type](refTy), None, "listOf", typeParams.map(transform[Type]), isFunctionRef = true),
        params.map(transform[Expr]),
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    //Array(1,2,3) --> arrayOf(1,2,3)
    case CallExpr(exprType,
    RefExpr(refTy, Some(RefExpr(_, None, "Array", typeParams, false)), "apply", _, _),
    params,
    paramsExpectedTypes) =>
      CallExpr(
        transform[Type](exprType),
        RefExpr(transform[Type](refTy), None, "arrayOf", typeParams.map(transform[Type]), isFunctionRef = true),
        params.map(transform[Expr]),
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // Seq.empty[T] --> emptyList<T>()
    case CallExpr(_,
    RefExpr(_, Some(RefExpr(_, None, "scala.Seq" | "scala.List", _, false)), "empty", typeParams, _),
    Seq(),
    _) =>
      if (typeParams.isEmpty) Exprs.emptyList
      else Exprs.emptyList(transform[Type](typeParams.head))

    //Array.empty[T] --> emptyList<T>()
    case CallExpr(_,
    RefExpr(_, Some(RefExpr(ty, None, "Array", _, false)), "empty", typeParams, _),
    Seq(), _) =>
      if (typeParams.isEmpty) Exprs.simpleCall("emptyArray", ty, Seq.empty)
      else CallExpr(listType(ty), RefExpr(ty, None, "emptyArray", Seq(ty), isFunctionRef = true), Seq.empty, Seq.empty)

    // Nil --> emptyList()
    case RefExpr(GenericType(KotlinTypes.LIST, _), None, "scala.Nil", _, false) =>
      Exprs.emptyList

    // (1 :: seq, 1 +: seq)  --> listOf(1) + seq
    case CallExpr(exprType, RefExpr(_, Some(left), "::" | "+:", _, _), Seq(right), paramsExpectedTypes) =>
      //if TypeUtils.isKotlinList(right.exprType) =>
      Exprs.simpleInfix(
        Exprs.listType(transform[Type](exprType)),
        "+",
        CallExpr(
          transform[Type](exprType),
          RefExpr(transform[Type](exprType), None, "listOf", Seq.empty, isFunctionRef = true),
          Seq(transform[Expr](right)),
          paramsExpectedTypes.map(transform[CallParameterInfo])
        ),
        transform[Expr](left)
      )

    // seq :+ 1  --> seq + 1
    case CallExpr(exprType, RefExpr(_, Some(_), ":+", _, true), Seq(right), _) =>
      Exprs.simpleInfix(Exprs.listType(transform[Type](exprType)), "+", transform[Expr](right), transform[Expr](right))

    // seq.mkString(a, b, c) --> seq.joinToString(b, a, c)
    case CallExpr(exprType,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "mkString", typeParams, true),
    params,
    paramsExpectedTypes) =>
      val newParams =
        if (params.length == 3) Seq(params(1), params.head, params(2))
        else params
      CallExpr(
        exprType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "joinToString", typeParams, isFunctionRef = true),
        newParams,
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // seq.tail --> seq.drop(1)
    case CallExpr(exprType,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "tail", typeParams, true),
    _,
    paramsExpectedTypes) =>
      CallExpr(
        exprType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "drop", typeParams, isFunctionRef = true),
        Seq(LitExpr(StdTypes.INT, "1")),
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // seq.head --> seq.first
    case CallExpr(exprType,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "head", typeParams, true),
    _,
    paramsExpectedTypes) =>
      CallExpr(
        exprType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "first", typeParams, isFunctionRef = true),
        Seq.empty,
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // seq.init --> seq.dropLast(1)
    case CallExpr(exprType,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "init", typeParams, true),
    _,
    paramsExpectedTypes) =>
      CallExpr(
        exprType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "dropLast", typeParams, isFunctionRef = true),
        Seq(LitExpr(StdTypes.INT, "1")),
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // seq.foreach --> seq.forEach
    case CallExpr(exprType,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "foreach", typeParams, true),
    params,
    paramsExpectedTypes) =>
      CallExpr(
        exprType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "forEach", typeParams, isFunctionRef = true),
        params.map(transform[Expr]),
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // seq.forall --> seq.all
    case CallExpr(exprType,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "forall", typeParams, true),
    params,
    paramsExpectedTypes) =>
      CallExpr(
        exprType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "all", typeParams, isFunctionRef = true),
        params.map(transform[Expr]),
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // str * i => str.repeat(i)
    case CallExpr(exprType,
    RefExpr(_, Some(left@WithType(StdTypes.STRING)), "*", _, _),
    Seq(right@WithType(StdTypes.INT)),
    paramsExpectedTypes) =>
      CallExpr(exprType,
        RefExpr(exprType, Some(transform[Expr](left)), "repeat", Seq.empty, isFunctionRef = true),
        Seq(right),
        paramsExpectedTypes.map(transform[CallParameterInfo]))

    // seq(i) --> seq[i]
    case CallExpr(exprType,
    RefExpr(_, Some(referenceObject@WithType(KotlinList(_))), "apply", _, true),
    Seq(i),
    _) =>
      BracketsExpr(exprType, transform[Expr](referenceObject), transform[Expr](i))

    // seq1 ++ seq2  --> seq1 + seq2
    case CallExpr(exprType,
    RefExpr(_, Some(left@WithType(KotlinList(_))), "++", _, true),
    Seq(right@WithType(KotlinList(_))),
    _) =>
      Exprs.simpleInfix(exprType, "+", transform[Expr](left), transform[Expr](right))

    // seq.nonEmpty --> seq.isNotEmpty
    case CallExpr(exprType,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "nonEmpty", typeParams, _),
    _,
    paramsExpectedTypes) =>
      CallExpr(
        exprType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "isNotEmpty", typeParams, isFunctionRef = true),
        Seq.empty,
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    // seq.size() --> seq.size
    case CallExpr(_,
    RefExpr(refTy, Some(referenceObject@WithType(KotlinList(_))), "size", typeParams, _),
    _,
    _) =>
      RefExpr(refTy, Some(transform[Expr](referenceObject)), "size", typeParams, isFunctionRef = true)

    // seqOfOptions.flatten --> seqOfOptions.filterNotNull()
    case CallExpr(callType,
    RefExpr(refTy, Some(referenceObject@WithType(ListType(NullableType(_)))), "flatten", typeParams, _),
    _,
    paramsExpectedTypes) =>
      CallExpr(
        callType,
        RefExpr(refTy, Some(transform[Expr](referenceObject)), "filterNotNull", typeParams, isFunctionRef = true),
        Seq.empty,
        paramsExpectedTypes.map(transform[CallParameterInfo])
      )

    case ApplyCall(WithType(ScalaType("scala.Option$")), Seq(p)) =>
      transform[Expr](p)

    // Pairs

    // 1 -> 2 --> 1 to 2
    case CallExpr(exprType, RefExpr(_, Some(left), "->", _, true), Seq(right), _) =>
      Exprs.simpleInfix(exprType, "to", transform[Expr](left), transform[Expr](right))

    // p._1 --> p.first
    // p._2 --> p.second
    case RefExpr(refTy, Some(left@WithType(GenericType(KotlinTypes.PAIR, _))), index@("_1" | "_2"), _, false) =>
      RefExpr(refTy, Some(transform[Expr](left)), if (index == "_1") "first" else "second", Seq.empty, isFunctionRef = false)

    // Some(x) --> x
    case a@ApplyCall(WithType(ScalaType("scala.util.Try$")), Seq(p)) =>
      Exprs.simpleCall("runTry", a.exprType, Seq(transform[Expr](p)))

    case RefExpr(_, Some(referenceObject), "asInstanceOf", Seq(typeParam), false) =>
      ParenthesesExpr(Exprs.asExpr(transform[Expr](referenceObject), typeParam))

    case RefExpr(_, Some(referenceObject), "isInstanceOf", Seq(typeParam), false) =>
      ParenthesesExpr(Exprs.isExpr(transform[Expr](referenceObject), typeParam))
  }

  override def name: String = "Transforming collections"
}
