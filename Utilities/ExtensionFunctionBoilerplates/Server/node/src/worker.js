"use strict";

const path = require("node:path");
const { processRequest } = require("./businessLogic");
const { loadFunctionConfig } = require("./config/functionConfig");
const { startRequestListener } = require("./servicebus/serviceBusRequestListener");

// Entry point of the standalone worker: reads the settings, starts listening, and runs until stopped.
// Optional argument: path to settings.json (default config/settings.json in the project folder).
const settingsPath = process.argv[2] || path.join(__dirname, "..", "config", "settings.json");
const config = loadFunctionConfig(settingsPath);
const listener = startRequestListener(config, processRequest);
console.log(`Listening on ${config.sourceQueue}, replying to ${config.destinationQueue}`);

let stopping = false;
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, async () => {
    if (stopping) {
      return;
    }
    stopping = true;
    console.log("Stopping...");
    await listener.close();
    process.exit(0);
  });
}
