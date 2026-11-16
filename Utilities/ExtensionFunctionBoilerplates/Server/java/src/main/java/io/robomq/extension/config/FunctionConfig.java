package io.robomq.extension.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONObject;

/** Connection and queue details from config/settings.json (same keys as the PowerShell boilerplate). */
public record FunctionConfig(String namespace, String keyName, String key, String sourceQueue, String destinationQueue) {
    private static final List<String> REQUIRED = List.of("namespace", "keyName", "key", "sourceQueue", "destinationQueue");

    public static FunctionConfig load(Path path) throws IOException {
        JSONObject json = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
        String missing = REQUIRED.stream()
                .filter(k -> json.optString(k).isBlank() || json.optString(k).startsWith("<"))
                .collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw new IllegalStateException(path + ": fill in " + missing);
        }
        return new FunctionConfig(json.getString("namespace"), json.getString("keyName"), json.getString("key"),
                json.getString("sourceQueue"), json.getString("destinationQueue"));
    }

    public String connectionString() {
        return "Endpoint=sb://" + namespace + ".servicebus.windows.net/;SharedAccessKeyName=" + keyName + ";SharedAccessKey=" + key;
    }
}
