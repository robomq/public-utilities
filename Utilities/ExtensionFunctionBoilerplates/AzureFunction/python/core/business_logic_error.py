"""The error your business logic raises to report a failure to H2R with its own error code."""


class BusinessLogicError(Exception):
    """Raise this to send a failure reply with your own error code:

        raise BusinessLogicError("Email is missing", "400")
    """

    def __init__(self, message: str, error_code: str):
        super().__init__(message)
        self.error_code = str(error_code)
