namespace RoboMQ.Extension.Core;

/// <summary>Thrown by business logic to report a failure to H2R with its own error code.</summary>
public class BusinessLogicException(string message, string errorCode) : Exception(message)
{
    /// <summary>Sent to H2R as <c>error_code</c>.</summary>
    public string ErrorCode { get; } = errorCode;
}
