using System.Text.Json;
using System.Text.Json.Nodes;

namespace RoboMQ.Extension.Core;

/// <summary>
/// Runs the business logic for one H2R request and creates the reply defined by the H2R extensionFunction contract.
/// Do not change the reply shape without the H2R team. You do not need to change this file.
/// </summary>
public class ExtensionRequestService(IExtensionLogic businessLogic)
{
    private const string UnexpectedErrorCode = "500";

    /// <summary>
    /// Creates the reply for one request.
    /// <para>Always returns a reply (success or failure); a failure inside the business logic is turned into a
    /// failure reply, never thrown.</para>
    /// </summary>
    /// <returns>The reply as JSON text.</returns>
    public async Task<string> CreateReplyAsync(string requestBody, string? correlationId)
    {
        JsonObject request;
        try
        {
            request = JsonNode.Parse(requestBody) as JsonObject
                      ?? throw new JsonException("Request body is not a JSON object");
        }
        catch (JsonException e)
        {
            Console.Error.WriteLine($"Request {correlationId} is not a JSON object");
            return Failure(UnexpectedErrorCode, e.Message);
        }

        try
        {
            return Success(await businessLogic.ProcessAsync(request, correlationId));
        }
        catch (BusinessLogicException e)
        {
            Console.Error.WriteLine($"Business logic reported failure {e.ErrorCode} for request {correlationId}");
            return Failure(e.ErrorCode, e.Message);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"Business logic failed for request {correlationId}: {e}");
            return Failure(UnexpectedErrorCode, e.Message);
        }
    }

    private static string Success(JsonObject? data)
    {
        var reply = new JsonObject
        {
            ["status"] = "success",
            ["timestamp"] = DateTime.UtcNow.ToString("o"),
        };
        if (data is not null)
        {
            // A JSON node can belong to only one parent; copying lets the logic return part of the request.
            reply["data"] = data.DeepClone();
        }
        return reply.ToJsonString();
    }

    private static string Failure(string errorCode, string errorDescription) =>
        new JsonObject
        {
            ["status"] = "failure",
            ["timestamp"] = DateTime.UtcNow.ToString("o"),
            ["error_code"] = errorCode,
            ["error_description"] = errorDescription,
        }.ToJsonString();
}
