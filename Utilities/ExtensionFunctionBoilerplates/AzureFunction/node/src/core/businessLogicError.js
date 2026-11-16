"use strict";

const BUSINESS_LOGIC_ERROR = "BusinessLogicError";

/**
 * Creates an error that reports a failure to H2R with your own error code:
 *   throw businessLogicError("Email is missing", "400");
 *
 * @param {string} message   sent to H2R as error_description
 * @param {string} errorCode sent to H2R as error_code
 * @returns {Error}
 */
function businessLogicError(message, errorCode) {
  const error = new Error(message);
  error.name = BUSINESS_LOGIC_ERROR;
  error.errorCode = String(errorCode);
  return error;
}

/** @returns {boolean} true if the error was created with businessLogicError() */
function isBusinessLogicError(error) {
  return error instanceof Error && error.name === BUSINESS_LOGIC_ERROR;
}

module.exports = { businessLogicError, isBusinessLogicError };
