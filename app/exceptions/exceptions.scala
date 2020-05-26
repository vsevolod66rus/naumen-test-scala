package object exceptions {
  sealed abstract class ApiException(private val message: String)
      extends Exception(message)

  final case class InvalidInputException(private val message: String)
      extends ApiException(message)
}
