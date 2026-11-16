using System.Text.Json.Nodes;

namespace RoboMQ.Extension.Core;

/// <summary>The customer's business logic. <c>BusinessLogic</c> implements it; you do not need to change this file.</summary>
public interface IExtensionLogic
{
    /// <summary>
    /// Runs the business logic for one request from H2R.
    /// <para>Returns null for a plain success, or a JsonObject that is sent back to H2R as <c>data</c>.</para>
    /// <para>Throw <see cref="BusinessLogicException"/> to report a failure with your own error code; any other
    /// exception is reported to H2R with error code 500.</para>
    /// <para>The same request can arrive more than once: Service Bus redelivers it if the reply could not be sent,
    /// even when this method already succeeded. Make repeated calls with the same request safe.</para>
    /// </summary>
    /// <param name="request">The request body from H2R, parsed from JSON.</param>
    /// <param name="correlationId">The ID of this request (null if H2R did not set one). Use it in log messages.</param>
    Task<JsonObject?> ProcessAsync(JsonObject request, string? correlationId);
}
