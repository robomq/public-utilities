using RoboMQ.Extension.Configuration;
using Xunit;

public class FunctionConfigTests
{
    private static string Write(string json)
    {
        var path = Path.Combine(Path.GetTempPath(), $"settings-{Guid.NewGuid()}.json");
        File.WriteAllText(path, json);
        return path;
    }

    [Fact]
    public void BuildsConnectionStringAndQueueNames()
    {
        var config = FunctionConfig.Load(Write("""{"namespace":"ns","keyName":"kn","key":"k","sourceQueue":"in","destinationQueue":"out"}"""));
        Assert.Equal("Endpoint=sb://ns.servicebus.windows.net/;SharedAccessKeyName=kn;SharedAccessKey=k", config.ConnectionString);
        Assert.Equal(("in", "out"), (config.SourceQueue, config.DestinationQueue));
    }

    [Fact]
    public void NamesEveryMissingOrPlaceholderValue()
    {
        var error = Assert.Throws<InvalidOperationException>(() =>
            FunctionConfig.Load(Write("""{"namespace":"ns","keyName":"kn","sourceQueue":"<Queue>","destinationQueue":"out"}""")));
        Assert.EndsWith("fill in key, sourceQueue", error.Message);
    }

    [Fact]
    public void ShippedTemplateIsRejectedUntilFilledIn()
    {
        var shipped = Path.Combine(AppContext.BaseDirectory, "config", "settings.json");
        var error = Assert.Throws<InvalidOperationException>(() => FunctionConfig.Load(shipped));
        Assert.Contains("fill in namespace", error.Message);
    }
}
