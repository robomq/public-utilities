"use strict";

const { isBusinessLogicError } = require("./businessLogicError");

// Builds the reply defined by the H2R extensionFunction contract. Do not change its shape without the H2R team.
// You do not need to change this file.

const UNEXPECTED_ERROR_CODE = "500";

/**
 * Runs the business logic for one request and creates the reply for H2R.
 *
 * Postconditions: always resolves to a reply object (success or failure); a failure inside the business logic is
 * turned into a failure reply, never thrown.
 * Side effects: runs the business logic.
 *
 * @param {(request: object, correlationId: string) => Promise<object|null|undefined>} processRequest the business logic
 * @param {object|string|Buffer} requestBody the request body; Service Bus may hand it over already parsed
 * @param {string} correlationId
 * @returns {Promise<object>} the reply to send to H2R
 */
async function createReply(processRequest, requestBody, correlationId) {
  let request;
  try {
    request = parseRequest(requestBody);
  } catch (error) {
    console.warn(`Request ${correlationId} is not a JSON object`);
    return failure(UNEXPECTED_ERROR_CODE, error.message);
  }
  try {
    return success(await processRequest(request, correlationId));
  } catch (error) {
    if (isBusinessLogicError(error)) {
      console.warn(`Business logic reported failure ${error.errorCode} for request ${correlationId}`);
      return failure(error.errorCode, error.message);
    }
    console.error(`Business logic failed for request ${correlationId}`, error);
    return failure(UNEXPECTED_ERROR_CODE, (error && error.message) || String(error));
  }
}

function parseRequest(requestBody) {
  const request =
    typeof requestBody === "string" || Buffer.isBuffer(requestBody) ? JSON.parse(requestBody.toString("utf8")) : requestBody;
  if (request === null || typeof request !== "object" || Array.isArray(request)) {
    throw new Error("Request body is not a JSON object");
  }
  return request;
}

function success(data) {
  const reply = { status: "success", timestamp: new Date().toISOString() };
  if (data !== null && data !== undefined) {
    reply.data = data;
  }
  return reply;
}

function failure(errorCode, errorDescription) {
  return {
    status: "failure",
    timestamp: new Date().toISOString(),
    error_code: errorCode,
    error_description: errorDescription,
  };
}

module.exports = { createReply };
