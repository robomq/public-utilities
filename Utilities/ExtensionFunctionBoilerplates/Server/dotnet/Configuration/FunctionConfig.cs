using System.Text;
using System.Text.Json.Nodes;

namespace RoboMQ.Extension.Configuration;

/// <summary>Connection and queue details from config/settings.json (same keys as the PowerShell, Java and Node boilerplates).</summary>
public record FunctionConfig(string Namespace, string KeyName, string Key, string SourceQueue, string DestinationQueue)
{
    private static readonly string[] RequiredKeys = ["namespace", "keyName", "key", "sourceQueue", "destinationQueue"];

    public string ConnectionString =>
        $"Endpoint=sb://{Namespace}.servicebus.windows.net/;SharedAccessKeyName={KeyName};SharedAccessKey={Key}";

    /// <summary>Loads the file and stops with one error that names every value that is missing or still a &lt;placeholder&gt;.</summary>
    public static FunctionConfig Load(string path)
    {
        var settings = JsonNode.Parse(File.ReadAllText(path, Encoding.UTF8)) as JsonObject
                       ?? throw new InvalidOperationException($"{path}: must contain a JSON object");
        var values = RequiredKeys.ToDictionary(key => key, key => ReadString(settings, key));
        var missing = RequiredKeys.Where(key => values[key].Trim() == "" || values[key].StartsWith('<')).ToList();
        if (missing.Count > 0)
        {
            throw new InvalidOperationException($"{path}: fill in {string.Join(", ", missing)}");
        }
        return new FunctionConfig(values["namespace"], values["keyName"], values["key"], values["sourceQueue"], values["destinationQueue"]);
    }

    private static string ReadString(JsonObject settings, string key) =>
        settings[key] is JsonValue value && value.TryGetValue(out string? text) ? text : "";
}
