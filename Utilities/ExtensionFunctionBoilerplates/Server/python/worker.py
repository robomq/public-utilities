"""Entry point of the standalone worker: reads the settings, listens for requests, and runs until stopped.

Usage: python worker.py [path/to/settings.json]   (default: config/settings.json next to this file)
"""

import logging
import signal
import sys
import threading
from pathlib import Path

from business_logic import process_request
from configuration.function_config import load_function_config
from servicebus.request_listener import run_request_listener

log = logging.getLogger("worker")


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
    logging.getLogger("azure").setLevel(logging.WARNING)  # the SDK is very chatty at INFO

    settings_path = sys.argv[1] if len(sys.argv) > 1 else Path(__file__).parent / "config" / "settings.json"
    config = load_function_config(settings_path)

    # Ctrl+C or SIGTERM: stop taking new requests; the current one finishes or goes back to the queue.
    stop_requested = threading.Event()
    for stop_signal in (signal.SIGINT, signal.SIGTERM):
        signal.signal(stop_signal, lambda *_: stop_requested.set())

    log.info("Listening on %s, replying to %s", config.source_queue, config.destination_queue)
    run_request_listener(config, process_request, stop_requested)
    log.info("Stopped")


if __name__ == "__main__":
    main()
