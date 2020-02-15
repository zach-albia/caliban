package caliban.client

import scala.collection.immutable.{ Map => SMap }
import caliban.client.CalibanClientError.{ CommunicationError, DecodingError, ServerError }
import caliban.client.FieldBuilder.Scalar
import caliban.client.Operations.IsOperation
import caliban.client.Selection.Directive
import caliban.client.Value.ObjectValue
import io.circe.parser
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri

sealed trait SelectionBuilder[-Origin, +A] { self =>

  private[caliban] def toSelectionSet: List[Selection]
  private[caliban] def fromGraphQL(value: Value): Either[DecodingError, A]

  def ~[Origin1 <: Origin, B](that: SelectionBuilder[Origin1, B]): SelectionBuilder[Origin1, (A, B)] =
    SelectionBuilder.Concat(self, that)

  def map[B](f: A => B): SelectionBuilder[Origin, B] = SelectionBuilder.Mapping(self, f)

  def withDirective(directive: Directive): SelectionBuilder[Origin, A]

  def withAlias(alias: String): SelectionBuilder[Origin, A]

  def toRequest[A1 >: A, Origin1 <: Origin](
    uri: Uri,
    useVariables: Boolean = false
  )(implicit ev: IsOperation[Origin1]): Request[Either[CalibanClientError, A1], Nothing] = {
    val (fields, variables) = SelectionBuilder.toGraphQL(toSelectionSet, useVariables)
    val variableDef =
      if (variables.nonEmpty)
        s"(${variables.map { case (name, (_, typeName)) => s"$$$name: $typeName" }.mkString(",")})"
      else ""
    val operation = s"${ev.operationName}$variableDef{$fields}"
    val request   = GraphQLRequest(operation, variables.map { case (k, (v, _)) => k -> v })

    basicRequest
      .post(uri)
      .body(request)
      .mapResponse { response =>
        for {
          resp <- response.left.map(CommunicationError(_))
          parsed <- parser
                     .decode[GraphQLResponse](resp)
                     .left
                     .map(ex => DecodingError("Json deserialization error", Some(ex)))
          data <- if (parsed.errors.nonEmpty) Left(ServerError(parsed.errors)) else Right(parsed.data)
          objectValue <- data match {
                          case o: ObjectValue => Right(o)
                          case _              => Left(DecodingError("Result is not an object"))
                        }
          result <- fromGraphQL(objectValue)
        } yield result
      }
  }

  def mapN[B, C, Res](f: (B, C) => Res)(implicit ev: A <:< (B, C)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case (b, c) => f(b, c) })
  def mapN[B, C, D, Res](f: (B, C, D) => Res)(implicit ev: A <:< ((B, C), D)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case ((b, c), d) => f(b, c, d) })
  def mapN[B, C, D, E, Res](
    f: (B, C, D, E) => Res
  )(implicit ev: A <:< (((B, C), D), E)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case (((b, c), d), e) => f(b, c, d, e) })
  def mapN[B, C, D, E, F, Res](
    f: (B, C, D, E, F) => Res
  )(implicit ev: A <:< ((((B, C), D), E), F)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case ((((b, c), d), e), ff) => f(b, c, d, e, ff) })
  def mapN[B, C, D, E, F, G, Res](
    f: (B, C, D, E, F, G) => Res
  )(implicit ev: A <:< (((((B, C), D), E), F), G)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case (((((b, c), d), e), ff), g) => f(b, c, d, e, ff, g) })
  def mapN[B, C, D, E, F, G, H, Res](
    f: (B, C, D, E, F, G, H) => Res
  )(implicit ev: A <:< ((((((B, C), D), E), F), G), H)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case ((((((b, c), d), e), ff), g), h) => f(b, c, d, e, ff, g, h) })
  def mapN[B, C, D, E, F, G, H, I, Res](
    f: (B, C, D, E, F, G, H, I) => Res
  )(implicit ev: A <:< (((((((B, C), D), E), F), G), H), I)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case (((((((b, c), d), e), ff), g), h), i) => f(b, c, d, e, ff, g, h, i) })
  def mapN[B, C, D, E, F, G, H, I, J, Res](
    f: (B, C, D, E, F, G, H, I, J) => Res
  )(implicit ev: A <:< ((((((((B, C), D), E), F), G), H), I), J)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case ((((((((b, c), d), e), ff), g), h), i), j) => f(b, c, d, e, ff, g, h, i, j) })
  def mapN[B, C, D, E, F, G, H, I, J, K, Res](
    f: (B, C, D, E, F, G, H, I, J, K) => Res
  )(implicit ev: A <:< (((((((((B, C), D), E), F), G), H), I), J), K)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen { case (((((((((b, c), d), e), ff), g), h), i), j), k) => f(b, c, d, e, ff, g, h, i, j, k) })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L) => Res
  )(implicit ev: A <:< ((((((((((B, C), D), E), F), G), H), I), J), K), L)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case ((((((((((b, c), d), e), ff), g), h), i), j), k), l) => f(b, c, d, e, ff, g, h, i, j, k, l)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M) => Res
  )(implicit ev: A <:< (((((((((((B, C), D), E), F), G), H), I), J), K), L), M)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case (((((((((((b, c), d), e), ff), g), h), i), j), k), l), m) => f(b, c, d, e, ff, g, h, i, j, k, l, m)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N) => Res
  )(implicit ev: A <:< ((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N)): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case ((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n) => f(b, c, d, e, ff, g, h, i, j, k, l, m, n)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O) => Res
  )(
    implicit ev: A <:< (((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O)
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case (((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Res
  )(
    implicit ev: A <:< ((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P)
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case ((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => Res
  )(
    implicit ev: A <:< (((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P), Q)
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case (((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p), q) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p, q)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => Res
  )(
    implicit ev: A <:< ((((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P), Q), R)
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case ((((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p), q), r) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p, q, r)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => Res
  )(
    implicit ev: A <:< (((((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P), Q), R), S)
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case (((((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p), q), r), s) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p, q, r, s)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => Res
  )(
    implicit ev: A <:< ((((((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P), Q), R), S), T)
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case ((((((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p), q), r), s), t) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p, q, r, s, t)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => Res
  )(
    implicit ev: A <:< (((((((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P), Q), R), S), T), U)
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case (((((((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p), q), r), s), t), u) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => Res
  )(
    implicit ev: A <:< (
      (((((((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P), Q), R), S), T), U),
      V
    )
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case ((((((((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p), q), r), s), t), u), v) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v)
    })
  def mapN[B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, Res](
    f: (B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W) => Res
  )(
    implicit ev: A <:< (
      ((((((((((((((((((((B, C), D), E), F), G), H), I), J), K), L), M), N), O), P), Q), R), S), T), U), V),
      W
    )
  ): SelectionBuilder[Origin, Res] =
    self.map(ev.andThen {
      case (
          ((((((((((((((((((((b, c), d), e), ff), g), h), i), j), k), l), m), n), o), p), q), r), s), t), u), v),
          w
          ) =>
        f(b, c, d, e, ff, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w)
    })
}

object SelectionBuilder {

  val __typename: SelectionBuilder[Any, String] = Field("__typename", Scalar[String]())

  case class Field[Origin, A](
    name: String,
    builder: FieldBuilder[A],
    alias: Option[String] = None,
    arguments: List[Argument[_]] = Nil,
    directives: List[Directive] = Nil
  ) extends SelectionBuilder[Origin, A] { self =>
    override def fromGraphQL(value: Value): Either[DecodingError, A] =
      value match {
        case ObjectValue(fields) =>
          fields.find {
            case (o, _) => alias.getOrElse(name) + math.abs(self.hashCode) == o || alias.contains(o) || name == o
          }.toRight(DecodingError(s"Missing field $name"))
            .flatMap(v => builder.fromGraphQL(v._2))
        case _ => Left(DecodingError(s"Invalid field type $name"))
      }

    override def withDirective(directive: Directive): SelectionBuilder[Origin, A] =
      self.copy(directives = directive :: directives)

    override def toSelectionSet: List[Selection] =
      List(Selection.Field(alias, name, arguments, directives, builder.toSelectionSet, self.hashCode))

    override def withAlias(alias: String): SelectionBuilder[Origin, A] = self.copy(alias = Some(alias))
  }
  case class Concat[Origin, A, B](first: SelectionBuilder[Origin, A], second: SelectionBuilder[Origin, B])
      extends SelectionBuilder[Origin, (A, B)] { self =>
    override def fromGraphQL(value: Value): Either[DecodingError, (A, B)] =
      for {
        v1 <- first.fromGraphQL(value)
        v2 <- second.fromGraphQL(value)
      } yield (v1, v2)

    override def withDirective(directive: Directive): SelectionBuilder[Origin, (A, B)] =
      Concat(first.withDirective(directive), second.withDirective(directive))

    override def toSelectionSet: List[Selection] = first.toSelectionSet ++ second.toSelectionSet

    override def withAlias(alias: String): SelectionBuilder[Origin, (A, B)] = self // makes no sense, do nothing
  }
  case class Mapping[Origin, A, B](builder: SelectionBuilder[Origin, A], f: A => B)
      extends SelectionBuilder[Origin, B] {
    override def fromGraphQL(value: Value): Either[DecodingError, B] = builder.fromGraphQL(value).map(f)

    override def withDirective(directive: Directive): SelectionBuilder[Origin, B] =
      Mapping(builder.withDirective(directive), f)

    override def toSelectionSet: List[Selection] = builder.toSelectionSet

    override def withAlias(alias: String): SelectionBuilder[Origin, B] = Mapping(builder.withAlias(alias), f)
  }

  def toGraphQL(
    fields: List[Selection],
    useVariables: Boolean,
    variables: SMap[String, (Value, String)] = SMap()
  ): (String, SMap[String, (Value, String)]) = {
    val fieldNames = fields.collect { case f: Selection.Field => f }.groupBy(_.name).map { case (k, v) => k -> v.size }
    val (fields2, variables2) = fields
      .foldLeft((List.empty[String], variables)) {
        case ((fields, variables), Selection.InlineFragment(onType, selection)) =>
          val (f, v) = toGraphQL(selection, useVariables, variables)
          (s"... on $onType{$f}" :: fields, v)

        case ((fields, variables), Selection.Field(alias, name, arguments, directives, selection, code)) =>
          // format arguments
          val (args, variables2) = arguments
            .foldLeft((List.empty[String], variables)) {
              case ((args, variables), a) =>
                val (a2, v2) = a.toGraphQL(useVariables, variables)
                (a2 :: args, v2)
            }
          val argString = args.filterNot(_.isEmpty).reverse.mkString(",") match {
            case ""   => ""
            case args => s"($args)"
          }

          // format directives
          val (dirs, variables3) = directives
            .foldLeft((List.empty[String], variables2)) {
              case ((dirs, variables), d) =>
                val (d2, v2) = d.toGraphQL(useVariables, variables)
                (d2 :: dirs, v2)
            }
          val dirString = dirs.reverse.mkString(" ") match {
            case ""   => ""
            case dirs => s" $dirs"
          }

          // format aliases
          val aliasString = (if (fieldNames.get(alias.getOrElse(name)).exists(_ > 1))
                               Some(alias.getOrElse(name) + math.abs(code))
                             else alias).fold("")(_ + ":")

          // format selection
          val (sel, variables4) = toGraphQL(selection, useVariables, variables3)
          val selString         = if (sel.nonEmpty) s"{$sel}" else ""

          (s"$aliasString$name$argString$dirString$selString" :: fields, variables4)
      }
    (fields2.reverse.mkString(" "), variables2)
  }
}
