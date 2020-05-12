package caliban.federation

import caliban.CalibanError.ValidationError
import caliban.GraphQL._
import caliban.Macros.gqldoc
import caliban.TestUtils._
import zio.test.Assertion._
import zio.test._
import zio.zquery.ZQuery

object FederationSpec extends DefaultRunnableSpec {
  val entityResolver =
    EntityResolver[Any, CharacterArgs, Character](args => ZQuery.succeed(characters.find(_.name == args.name)))

  override def spec = suite("FederationSpec")(
    testM("should resolve federated types") {
      val interpreter = federate(graphQL(resolver), entityResolver).interpreter

      val query = gqldoc("""
            query test {
              _entities(representations: [{__typename: "Character", name: "Amos Burton"}]) {
                  __typename
                  ... on Character {
                    name
                  }
              }
            }""")

      assertM(interpreter.flatMap(_.execute(query)).map(_.data.toString))(
        equalTo("""{"_entities":[{"__typename":"Character","name":"Amos Burton"}]}""")
      )
    },
    testM("should not include _entities if not resolvers provided") {
      val interpreter = federate(graphQL(resolver)).interpreter

      val query = gqldoc("""
            query test {
              _entities(representations: [{__typename: "Character", name: "Amos Burton"}]) {
                  __typename
                  ... on Character {
                    name
                  }
              }
            }""")

      assertM(interpreter.flatMap(_.execute(query)).map(_.errors))(
        equalTo(
          List(
            ValidationError(
              "Field '_entities' does not exist on type 'Query'.",
              "The target field of a field selection must be defined on the scoped type of the selection set. There are no limitations on alias names."
            )
          )
        )
      )
    }
  )
}
