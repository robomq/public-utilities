package io.robomq.extension;

import io.robomq.extension.config.FunctionConfig;
import io.robomq.extension.core.ExtensionRequestService;
import io.robomq.extension.servicebus.ServiceBusRequestListener;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/** Entry point of the standalone worker: reads the settings, starts listening, and runs until stopped. */
public final class Worker {
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private static final String DEFAULT_SETTINGS_PATH = "config/settings.json";

    private Worker() {
    }

    /** Optional argument: path to settings.json (default config/settings.json in the working directory). */
    public static void main(String[] args) throws Exception {
        FunctionConfig config = FunctionConfig.load(Path.of(args.length > 0 ? args[0] : DEFAULT_SETTINGS_PATH));
        ServiceBusRequestListener listener = new ServiceBusRequestListener(config.connectionString(),
                config.sourceQueue(), config.destinationQueue(), new ExtensionRequestService(new BusinessLogic()));

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            listener.close();
            stopped.countDown();
        }));
        listener.start();
        LOG.info("Listening on " + config.sourceQueue() + ", replying to " + config.destinationQueue());
        stopped.await();
    }
}
