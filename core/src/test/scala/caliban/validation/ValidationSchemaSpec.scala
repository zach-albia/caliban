package caliban.validation

import caliban.CalibanError.ValidationError
import caliban.GraphQL.graphQL
import caliban.TestUtils.InvalidSchemas._
import caliban.introspection.adt._
import caliban.{ GraphQL, RootResolver }
import zio.IO
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment

object ValidationSchemaSpec extends DefaultRunnableSpec {

  def check(gql: GraphQL[Any], expectedMessage: String): IO[ValidationError, TestResult] =
    assertM(gql.interpreter.run)(fails[ValidationError](hasField("msg", _.msg, equalTo(expectedMessage))))

  def checkTypeError(t: __Type, expectedMessage: String): IO[ValidationError, TestResult] =
    assertM(Validator.validateType(t).run)(fails(hasField("msg", _.msg, equalTo(expectedMessage))))

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("ValidationSchemaSpec")(
      suite("Enum")(
        testM("non-empty enum is ok") {
          assertM(
            Validator
              .validateEnum(
                __Type(
                  kind = __TypeKind.ENUM,
                  enumValues = _ => Some(List(__EnumValue(name = "A", isDeprecated = false)))
                )
              )
              .run
          )(succeeds(anything))
        },
        testM("must be non-empty") {
          checkTypeError(
            __Type(
              name = Some("EmptyEnum"),
              kind = __TypeKind.ENUM,
              enumValues = _ => None
            ),
            "Enum EmptyEnum doesn't contain any values"
          )
        }
      ),
      suite("Union")(
        testM("union containing object types is ok") {
          assertM(
            Validator
              .validateUnion(
                __Type(
                  kind = __TypeKind.UNION,
                  possibleTypes = Some(List(__Type(kind = __TypeKind.OBJECT)))
                )
              )
              .run
          )(succeeds(anything))
        },
        testM("must be non-empty") {
          val expectedMessage = "Union EmptyUnion doesn't contain any type."
          (checkTypeError(
            __Type(
              name = Some("EmptyUnion"),
              kind = __TypeKind.UNION,
              possibleTypes = None
            ),
            expectedMessage
          ) &&& checkTypeError(
            __Type(
              name = Some("EmptyUnion"),
              kind = __TypeKind.UNION,
              possibleTypes = Some(List.empty)
            ),
            expectedMessage
          )).map { case (a, b) => a && b }
        }
      ),
      suite("Directives")(
        testM("name on a type can't start with '__'") {
          check(
            graphQL(resolverWrongDirectiveName),
            "Directive '__name' of Type 'TestWrongDirectiveName' can't start with '__'"
          )
        },
        testM("name on a field type can't start with '__'") {
          check(
            graphQL(resolverWrongFieldDirectiveName),
            "Directive '__name' of Field 'field' of Type 'TestWrongFieldDirectiveName' can't start with '__'"
          )
        },
        testM("name on a inputValue on a type can't start with '__'") {
          check(
            graphQL(resolverWrongInputFieldDirectiveName),
            "Directive '__name' of InputValue 'inputValue' of Type 'WrongDirectiveNameInput' can't start with '__'"
          )
        },
        testM("name on a inputValue on a field type can't start with '__'") {
          check(
            graphQL(resolverWrongFieldArgDirectiveName),
            "Directive '__name' of InputValue 'inputValue' of Field 'field' of Type 'TestWrongFieldArgDirectiveName' can't start with '__'"
          )
        },
        testM("argument name can't start with '__'") {
          check(
            graphQL(resolverWrongArgumentDirectiveName),
            "Argument '__name' of Directive 'name' of Field 'field' of Type 'TestWrongArgumentDirectiveName' can't start with '__'"
          )
        }
      ),
      suite("InputObjects")(
        testM("must define one or more fields") {
          (checkTypeError(
            __Type(
              name = Some("EmptyInputObject"),
              kind = __TypeKind.INPUT_OBJECT,
              inputFields = None
            ),
            "InputObject 'EmptyInputObject' does not have fields"
          ) &&&
            checkTypeError(
              __Type(
                name = Some("EmptyInputObject"),
                kind = __TypeKind.INPUT_OBJECT,
                inputFields = Some(List.empty)
              ),
              "InputObject 'EmptyInputObject' does not have fields"
            )).map { case (a, b) => a && b }
        },
        testM("no two input fields may share the same name") {
          checkTypeError(
            __Type(
              name = Some("DuplicateNamesInputObject"),
              kind = __TypeKind.INPUT_OBJECT,
              inputFields = Some(
                List.fill(2)(
                  __InputValue("A", None, `type` = () => __Type(__TypeKind.SCALAR), None)
                )
              )
            ),
            "InputObject 'DuplicateNamesInputObject' has repeated fields: A"
          )
        },
        testM("name can't start with '__'") {
          check(
            graphQL(resolverWrongMutationUnderscore),
            "InputValue '__name' of InputObject 'DoubleUnderscoreArgInput' can't start with '__'"
          )
        },
        testM("should only contain types for which IsInputType(type) is true") {
          check(
            graphQL(resolverWrongMutationUnion),
            "UnionInput of InputValue 'union' of InputObject 'UnionArgInput' is of kind UNION, must be an InputType"
          )
        }
      ), {
        import Interface._
        suite("Interface")(
          testM("must define one or more fields") {
            check(
              graphQL(resolverEmptyInferface),
              "Interface 'InterfaceEmpty' does not have fields"
            )
          },
          testM("field names must be unique") {
            checkTypeError(
              __Type(
                name = Some("DuplicateNamesInterface"),
                kind = __TypeKind.INTERFACE,
                fields = _ =>
                  Some(
                    List(
                      __Field("A", None, List.empty, `type` = () => __Type(__TypeKind.SCALAR)),
                      __Field("A", None, List.empty, `type` = () => __Type(__TypeKind.SCALAR))
                    )
                  )
              ),
              "Interface 'DuplicateNamesInterface' has repeated fields: A"
            )
          },
          testM("field name can't start with '__'") {
            check(
              graphQL(resolverInferfaceWrongFieldName),
              "Field '__name' of Interface 'InterfaceWrongFieldName' can't start with '__'"
            )
          },
          testM("field can't be input type") {
            checkTypeError(
              __Type(
                name = Some("InputTypeFieldInterface"),
                kind = __TypeKind.INTERFACE,
                fields = _ =>
                  Some(
                    List(
                      __Field(
                        "InputField",
                        None,
                        List.empty,
                        `type` = () => __Type(name = Some("InputType"), kind = __TypeKind.INPUT_OBJECT)
                      )
                    )
                  )
              ),
              "InputType of Field 'InputField' of Interface 'InputTypeFieldInterface' is of kind INPUT_OBJECT, must be an OutputType"
            )
          },
          testM("field argument name can't start with '__'") {
            check(
              graphQL(resolverInterfaceWrongArgumentName),
              "InputValue '__name' of Field 'x' of Interface 'InterfaceWrongArgumentName' can't start with '__'"
            )
          },
          testM("field argument can't be output type") {
            check(
              graphQL(resolverInterfaceWrongArgumentInputType),
              "UnionInput of InputValue 'union' of InputObject 'UnionArgInput' is of kind UNION, must be an InputType"
            )
          },
          testM("clashing input and object types") {
            check(
              graphQL(resolverClashingObjects),
              "Type 'ClashingObjectInput' is defined multiple times (INPUT_OBJECT in caliban.TestUtils.InvalidSchemas.Interface.ClashingObject, OBJECT in caliban.TestUtils.InvalidSchemas.Interface.ClashingObjectInput)."
            )
          },
          testM("clashing names from different packages") {
            check(
              graphQL(resolverClashingNames),
              "Type 'C' is defined multiple times (OBJECT in caliban.TestUtils.InvalidSchemas.Interface.A.C, OBJECT in caliban.TestUtils.InvalidSchemas.Interface.B.C)."
            )
          },
          testM("missing root query") {
            check(
              graphQL(RootResolver[Unit, Unit, Unit](None, None, None)),
              "The query root operation is missing."
            )
          }
        )
      }, {
        import Object._
        suite("Object")(
          testM("must define one or more fields") {
            check(
              graphQL(resolverEmpty),
              "Object 'EmptyObject' does not have fields"
            )
          },
          testM("field name can't start with '__'") {
            check(
              graphQL(resolverWrongFieldName),
              "Field '__name' of Object 'ObjectWrongFieldName' can't start with '__'"
            )
          },
          testM("field argument name can't start with '__'") {
            check(
              graphQL(resolverWrongArgumentName),
              "InputValue '__name' of Field 'x' of Object 'ObjectWrongArgumentName' can't start with '__'"
            )
          },
          testM("field argument can't be output type") {
            check(
              graphQL(resolverWrongArgumentInputType),
              "UnionInput of InputValue 'union' of InputObject 'UnionArgInput' is of kind UNION, must be an InputType"
            )
          },
          testM("may declare that it implements one or more unique interfaces") {
            assertM(graphQL(resolverTwoInterfaces).interpreter.run)(succeeds(anything))
          },
          testM("must include a field of the same name for every field defined in an interface") {
            checkTypeError(mkIncompleteObjectWithFields("a"), "Object 'IncompleteFieldsObject' is missing field(s): b") *>
              checkTypeError(
                mkIncompleteObjectWithFields("b"),
                "Object 'IncompleteFieldsObject' is missing field(s): a"
              ) *>
              checkTypeError(
                mkIncompleteObjectWithFields("c"),
                "Object 'IncompleteFieldsObject' is missing field(s): a, b"
              )
          }
        )
      }
    )
}
