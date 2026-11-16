"use strict";

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { loadFunctionConfig } = require("../../src/config/functionConfig");

const VALID = { namespace: "ns", keyName: "kn", key: "k", sourceQueue: "in", destinationQueue: "out" };
const write = (settings) => {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "settings-")), "settings.json");
  fs.writeFileSync(file, JSON.stringify(settings));
  return file;
};

test("builds the connection string and queue names", () => {
  const config = loadFunctionConfig(write(VALID));
  assert.strictEqual(config.connectionString, "Endpoint=sb://ns.servicebus.windows.net/;SharedAccessKeyName=kn;SharedAccessKey=k");
  assert.deepStrictEqual([config.sourceQueue, config.destinationQueue], ["in", "out"]);
});

test("names every missing or placeholder value", () => {
  const { key, ...withoutKey } = VALID; // eslint-disable-line no-unused-vars
  assert.throws(() => loadFunctionConfig(write({ ...withoutKey, sourceQueue: "<Queue>" })), /fill in key, sourceQueue$/);
});

test("shipped template is rejected until filled in", () => {
  assert.throws(() => loadFunctionConfig(path.join(__dirname, "..", "..", "config", "settings.json")), /fill in namespace/);
});
