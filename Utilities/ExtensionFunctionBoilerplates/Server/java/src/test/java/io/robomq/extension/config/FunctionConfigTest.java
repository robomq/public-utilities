package io.robomq.extension.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FunctionConfigTest {
    @TempDir
    Path dir;

    private Path write(String json) throws Exception {
        return Files.writeString(dir.resolve("settings.json"), json);
    }

    @Test
    void buildsConnectionString() throws Exception {
        FunctionConfig c = FunctionConfig.load(write("{\"namespace\":\"ns\",\"keyName\":\"kn\",\"key\":\"k\",\"sourceQueue\":\"in\",\"destinationQueue\":\"out\"}"));
        assertEquals("Endpoint=sb://ns.servicebus.windows.net/;SharedAccessKeyName=kn;SharedAccessKey=k", c.connectionString());
        assertEquals("in", c.sourceQueue());
        assertEquals("out", c.destinationQueue());
    }

    @Test
    void missingOrPlaceholderValuesAreNamed() throws Exception {
        Path p = write("{\"namespace\":\"ns\",\"keyName\":\"kn\",\"sourceQueue\":\"<Queue>\",\"destinationQueue\":\"out\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> FunctionConfig.load(p));
        assertTrue(e.getMessage().endsWith("fill in key, sourceQueue"), e.getMessage());
    }

    @Test
    void shippedTemplateIsRejectedUntilFilledIn() {
        assertThrows(IllegalStateException.class, () -> FunctionConfig.load(Path.of("config/settings.json")));
    }
}
