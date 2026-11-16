using System.Text.Json.Nodes;
using RoboMQ.Extension.Core;

namespace RoboMQ.Extension;

/// <summary>
/// Write your business logic in this class. This is the only file you need to change.
/// <para>One BusinessLogic object is created at startup and reused for every request, so fields you set in the
/// constructor or in <see cref="ProcessAsync"/> (API clients, tokens, caches) are kept between requests.
/// Requests are handled one at a time.</para>
/// </summary>
/// <example>
/// <code>
/// public class BusinessLogic : IExtensionLogic
/// {
///     private readonly MyApiClient _client;
///
///     public BusinessLogic()
///     {
///         _client = new MyApiClient();                              // one-time setup
///     }
///
///     public async Task&lt;JsonObject?&gt; ProcessAsync(JsonObject request, string? correlationId)
///     {
///         var email = request["email"]?.GetValue&lt;string&gt;();   // read a value from the request
///         if (string.IsNullOrEmpty(email))
///         {
///             throw new BusinessLogicException("Email is missing", "400");   // failure with your own code
///         }
///         var accountId = await _client.CreateAccountAsync(email);
///         return new JsonObject { ["accountId"] = accountId };      // optional: data sent back to H2R; or return null
///     }
/// }
/// </code>
/// </example>
public class BusinessLogic : IExtensionLogic
{
    public BusinessLogic()
    {
        // One-time setup goes here.
    }

    /// <inheritdoc />
    /// <remarks>Add <c>async</c> to this method when you need to <c>await</c> calls inside it.</remarks>
    public Task<JsonObject?> ProcessAsync(JsonObject request, string? correlationId)
    {
        return Task.FromResult<JsonObject?>(null);
    }
}
