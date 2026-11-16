"use strict";

const test = require("node:test");
const assert = require("node:assert");
const { createReply } = require("../../src/core/reply");
const { businessLogicError } = require("../../src/core/businessLogicError");

test("null result is success without data", async () => {
  const reply = await createReply(async () => null, { email: "a@b.com" }, "cid-1");
  assert.strictEqual(reply.status, "success");
  assert.ok(!("data" in reply));
  assert.ok(reply.timestamp.endsWith("Z"));
});

test("request and correlation id reach the logic and data is returned", async () => {
  const reply = await createReply(
    async (request, correlationId) => ({ echo: `${request.email}/${correlationId}` }),
    { email: "a@b.com" },
    "cid-1"
  );
  assert.deepStrictEqual(reply.data, { echo: "a@b.com/cid-1" });
});

test("string and Buffer bodies are parsed", async () => {
  for (const body of ['{"email":"a@b.com"}', Buffer.from('{"email":"a@b.com"}')]) {
    const reply = await createReply(async (request) => ({ email: request.email }), body, "cid-1");
    assert.deepStrictEqual(reply.data, { email: "a@b.com" });
  }
});

test("businessLogicError keeps its code", async () => {
  const reply = await createReply(async () => {
    throw businessLogicError("Email is missing", "400");
  }, {}, "cid-1");
  assert.deepStrictEqual([reply.status, reply.error_code, reply.error_description], ["failure", "400", "Email is missing"]);
  assert.ok(reply.timestamp.endsWith("Z"));
});

test("other error is 500", async () => {
  const reply = await createReply(async () => {
    throw new Error("boom");
  }, {}, "cid-1");
  assert.deepStrictEqual([reply.error_code, reply.error_description], ["500", "boom"]);
});

test("an error that only has an errorCode property is still 500", async () => {
  const reply = await createReply(async () => {
    throw Object.assign(new Error("sdk failure"), { errorCode: "429" });
  }, {}, "cid-1");
  assert.strictEqual(reply.error_code, "500");
});

test("thrown non-Error value still has a description", async () => {
  const reply = await createReply(async () => {
    throw "plain string"; // eslint-disable-line no-throw-literal
  }, {}, "cid-1");
  assert.deepStrictEqual([reply.error_code, reply.error_description], ["500", "plain string"]);
});

test("invalid JSON or non-object body is 500 and logic is not called", async () => {
  for (const body of ["not json", "[1,2]", "null", 42]) {
    const reply = await createReply(async () => assert.fail("business logic must not run"), body, "cid-1");
    assert.deepStrictEqual([reply.status, reply.error_code], ["failure", "500"]);
  }
});

test("state kept outside the function survives between requests", async () => {
  let count = 0;
  const processRequest = async () => ({ count: ++count });
  await createReply(processRequest, {}, "1");
  const reply = await createReply(processRequest, {}, "2");
  assert.strictEqual(reply.data.count, 2);
});
