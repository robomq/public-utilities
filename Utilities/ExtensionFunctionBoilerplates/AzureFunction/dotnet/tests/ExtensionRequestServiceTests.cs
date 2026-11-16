using System.Text.Json.Nodes;
using RoboMQ.Extension.Core;
using Xunit;

public class ExtensionRequestServiceTests
{
    private sealed class FakeLogic(Func<JsonObject, string?, Task<JsonObject?>> process) : IExtensionLogic
    {
        public Task<JsonObject?> ProcessAsync(JsonObject request, string? correlationId) => process(request, correlationId);
    }

    private static async Task<JsonObject> CreateReply(Func<JsonObject, string?, Task<JsonObject?>> process, string body) =>
        JsonNode.Parse(await new ExtensionRequestService(new FakeLogic(process)).CreateReplyAsync(body, "cid-1"))!.AsObject();

    [Fact]
    public async Task NullResultIsSuccessWithoutData()
    {
        var reply = await CreateReply((_, _) => Task.FromResult<JsonObject?>(null), """{"email":"a@b.com"}""");
        Assert.Equal("success", (string?)reply["status"]);
        Assert.False(reply.ContainsKey("data"));
        Assert.EndsWith("Z", (string?)reply["timestamp"]);
    }

    [Fact]
    public async Task RequestAndCorrelationIdReachLogicAndDataIsReturned()
    {
        var reply = await CreateReply(
            (request, correlationId) => Task.FromResult<JsonObject?>(new JsonObject { ["echo"] = $"{request["email"]}/{correlationId}" }),
            """{"email":"a@b.com"}""");
        Assert.Equal("a@b.com/cid-1", (string?)reply["data"]!["echo"]);
    }

    [Fact]
    public async Task ReturningPartOfTheRequestWorks()
    {
        var reply = await CreateReply((request, _) => Task.FromResult(request["address"] as JsonObject),
            """{"address":{"city":"Pune"}}""");
        Assert.Equal("Pune", (string?)reply["data"]!["city"]);
    }

    [Fact]
    public async Task BusinessLogicExceptionKeepsItsCode()
    {
        var reply = await CreateReply((_, _) => throw new BusinessLogicException("Email is missing", "400"), "{}");
        Assert.Equal(("failure", "400", "Email is missing"),
            ((string?)reply["status"], (string?)reply["error_code"], (string?)reply["error_description"]));
        Assert.EndsWith("Z", (string?)reply["timestamp"]);
    }

    [Fact]
    public async Task OtherExceptionIs500()
    {
        var reply = await CreateReply((_, _) => throw new InvalidOperationException("boom"), "{}");
        Assert.Equal(("500", "boom"), ((string?)reply["error_code"], (string?)reply["error_description"]));
    }

    [Fact]
    public async Task FaultedTaskIs500()
    {
        var reply = await CreateReply((_, _) => Task.FromException<JsonObject?>(new TimeoutException("API timed out")), "{}");
        Assert.Equal(("500", "API timed out"), ((string?)reply["error_code"], (string?)reply["error_description"]));
    }

    [Theory]
    [InlineData("not json")]
    [InlineData("[1,2]")]
    [InlineData("null")]
    [InlineData("42")]
    [InlineData("")]
    public async Task InvalidJsonOrNonObjectBodyIs500AndLogicIsNotCalled(string body)
    {
        var reply = await CreateReply((_, _) => throw new Xunit.Sdk.XunitException("business logic must not run"), body);
        Assert.Equal(("failure", "500"), ((string?)reply["status"], (string?)reply["error_code"]));
    }

    [Fact]
    public async Task StateInBusinessLogicIsKeptBetweenRequests()
    {
        var count = 0;
        var service = new ExtensionRequestService(new FakeLogic((_, _) => Task.FromResult<JsonObject?>(new JsonObject { ["count"] = ++count })));
        await service.CreateReplyAsync("{}", "1");
        var reply = JsonNode.Parse(await service.CreateReplyAsync("{}", "2"))!;
        Assert.Equal(2, (int)reply["data"]!["count"]!);
    }
}
