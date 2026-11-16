"use strict";

const fs = require("node:fs");

const REQUIRED_KEYS = ["namespace", "keyName", "key", "sourceQueue", "destinationQueue"];

/**
 * Connection and queue details from config/settings.json (same keys as the PowerShell and Java boilerplates).
 * Stops with one error that names every value that is missing or still a <placeholder>.
 *
 * @param {string} path
 * @returns {{ connectionString: string, sourceQueue: string, destinationQueue: string }}
 */
function loadFunctionConfig(path) {
  const settings = JSON.parse(fs.readFileSync(path, "utf8"));
  const missing = REQUIRED_KEYS.filter((key) => {
    const value = settings[key];
    return typeof value !== "string" || value.trim() === "" || value.startsWith("<");
  });
  if (missing.length > 0) {
    throw new Error(`${path}: fill in ${missing.join(", ")}`);
  }
  return {
    connectionString: `Endpoint=sb://${settings.namespace}.servicebus.windows.net/;SharedAccessKeyName=${settings.keyName};SharedAccessKey=${settings.key}`,
    sourceQueue: settings.sourceQueue,
    destinationQueue: settings.destinationQueue,
  };
}

module.exports = { loadFunctionConfig };
