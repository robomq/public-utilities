"use strict";

// eslint-disable-next-line no-unused-vars
const { businessLogicError } = require("./core/businessLogicError");

// Write your business logic in this file. This is the only file you need to change.
//
// One-time setup: code at the top level of this file runs once, when the program starts. Variables declared here
// (API clients, tokens, caches) are kept between requests. Requests are handled one at a time.
//
// Example:
//
//   const client = createMyApiClient(process.env.MyApiKey);      // one-time setup
//
//   async function processRequest(request, correlationId) {
//     const email = request.email;                               // read a value from the request
//     if (!email) {
//       throw businessLogicError("Email is missing", "400");     // failure with your own code
//     }
//     const accountId = await client.createAccount(email);
//     return { accountId };                                      // optional: data sent back to H2R; or return null
//   }

/**
 * Called once for every request from H2R.
 *
 * Return null for a plain success, or an object that is sent back to H2R as "data".
 * Throw businessLogicError(message, code) to report a failure with your own error code; any other error is reported
 * with code 500. The same request can arrive more than once (Service Bus redelivers it if the reply could not be
 * sent), so make repeated calls with the same request safe.
 *
 * @param {object} request       the request body from H2R, parsed from JSON
 * @param {string} correlationId the ID of this request (use it in your log messages)
 * @returns {Promise<object|null>}
 */
async function processRequest(request, correlationId) {
  return null;
}

module.exports = { processRequest };
