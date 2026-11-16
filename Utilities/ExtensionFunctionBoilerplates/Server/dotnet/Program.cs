using System.Runtime.InteropServices;
using RoboMQ.Extension;
using RoboMQ.Extension.Configuration;
using RoboMQ.Extension.Core;
using RoboMQ.Extension.ServiceBus;

// Entry point of the standalone worker: reads the settings, starts listening, and runs until stopped.
// Optional argument: path to settings.json (default: config/settings.json next to the program).
var settingsPath = args.Length > 0 ? args[0] : Path.Combine(AppContext.BaseDirectory, "config", "settings.json");
var config = FunctionConfig.Load(settingsPath);

await using var listener = new ServiceBusRequestListener(config, new ExtensionRequestService(new BusinessLogic()));

var stopRequested = new TaskCompletionSource();
using var onInterrupt = PosixSignalRegistration.Create(PosixSignal.SIGINT, context => { context.Cancel = true; stopRequested.TrySetResult(); });
using var onTerminate = PosixSignalRegistration.Create(PosixSignal.SIGTERM, context => { context.Cancel = true; stopRequested.TrySetResult(); });

await listener.StartAsync();
Console.WriteLine($"Listening on {config.SourceQueue}, replying to {config.DestinationQueue}");
await stopRequested.Task;
Console.WriteLine("Stopping...");
