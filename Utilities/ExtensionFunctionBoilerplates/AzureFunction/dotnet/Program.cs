using Microsoft.Azure.Functions.Worker.Builder;
using Microsoft.Extensions.Hosting;

// Starts the Azure Functions isolated worker. You do not need to change this file.
FunctionsApplication.CreateBuilder(args).Build().Run();
