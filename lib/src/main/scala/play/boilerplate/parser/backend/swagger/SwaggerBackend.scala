package play.boilerplate.parser.backend.swagger

import io.swagger.models.Swagger
import io.swagger.parser.SwaggerParser
import play.boilerplate.parser.backend.{ParserBackend, ParserException}
import play.boilerplate.parser.model._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object SwaggerBackend
  extends ParserBackend
    with OperationParser
    with ResponseParser
    with ModelParser
    with SecurityParser
    with ParameterParser
    with PropertyParser
    with ReferenceParser {

  override def parseSchema(fileName: String): Either[ParserException, Schema] = {

    (for {
      swagger <- Try(new SwaggerParser().read(fileName))
      schema = Option(swagger).map(parseSwagger).getOrElse {
        throw new RuntimeException(s"Parsing schema file is failed ($fileName).")
      }
    } yield schema) match {
      case Success(model) => Right(model)
      case Failure(cause) => Left(ParserException(cause.getMessage, cause))
    }

  }

  private def parseSwagger(swagger: Swagger): Schema = {

    val definitions = Option(swagger.getDefinitions)
      .map(_.asScala.toMap)
      .getOrElse(Map.empty)
      .map { case (name, model) =>
        name -> parseModel(Schema.empty, model)
      }

    val parameters = Option(swagger.getParameters)
      .map(_.asScala.toMap)
      .getOrElse(Map.empty)
      .map { case (name, param) =>
        param.setName(name)
        name -> parseParameter(Schema.empty, param)
      }

    val responses = Option(swagger.getResponses)
      .map(_.asScala.toMap)
      .getOrElse(Map.empty)
      .map { case (code, response) =>
        parseResponse(Schema.empty, code, response)
      }

    val securitySchemas = Option(swagger.getSecurityDefinitions)
      .map(_.asScala.toMap)
      .getOrElse(Map.empty)
      .map { case (name, schema) =>
        name -> parseSecuritySchema(name, schema)
      }

    val initial = Schema(
      host     = Option(swagger.getHost).getOrElse("localhost"),
      basePath = Option(swagger.getBasePath).getOrElse("/"),
      schemes  = Option(swagger.getSchemes).map(_.asScala).getOrElse(Nil).map(_.toValue),
      consumes = Option(swagger.getConsumes).map(_.asScala).getOrElse(Nil),
      produces = Option(swagger.getProduces).map(_.asScala).getOrElse(Nil),
      paths = Nil,
      //security: List[SecurityRequirement],
      securitySchemas = securitySchemas,
      definitions = definitions,
      parameters  = parameters,
      responses   = responses
    )

    initial.copy(
      paths = parsePaths(swagger, initial)
    )

  }

  private def parsePaths(swagger: Swagger, schema: Schema): Iterable[Path] = {

    for {
      (url, path) <- Option(swagger.getPaths.asScala.toMap).getOrElse(Map.empty)
    } yield {
      Path(
        pathUrl = url,
        pathParts = parsePathUrl(url),
        operations = parsePathOperations(schema, url, path)
      )
    }

  }

  private def parsePathUrl(url: String): Iterable[PathPart] = {

    val paramRx = """\{(.+)\}""".r

    url.split('/').filter(_.nonEmpty).map {
      case paramRx(name) => ParamPart(name)
      case s => StaticPart(s)
    }

  }

}
