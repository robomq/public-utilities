import json
import tempfile
import unittest
from pathlib import Path

from configuration.function_config import load_function_config

VALID = {"namespace": "ns", "keyName": "kn", "key": "k", "sourceQueue": "in", "destinationQueue": "out"}


def write(settings):
    file = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
    json.dump(settings, file)
    file.close()
    return file.name


class LoadFunctionConfigTest(unittest.TestCase):
    def test_builds_the_connection_string_and_queue_names(self):
        config = load_function_config(write(VALID))
        self.assertEqual(config.connection_string,
                         "Endpoint=sb://ns.servicebus.windows.net/;SharedAccessKeyName=kn;SharedAccessKey=k")
        self.assertEqual((config.source_queue, config.destination_queue), ("in", "out"))

    def test_names_every_missing_or_placeholder_value(self):
        settings = {**VALID, "sourceQueue": "<Queue>"}
        del settings["key"]
        with self.assertRaisesRegex(ValueError, "fill in key, sourceQueue$"):
            load_function_config(write(settings))

    def test_shipped_template_is_rejected_until_filled_in(self):
        shipped = Path(__file__).resolve().parent.parent / "config" / "settings.json"
        with self.assertRaisesRegex(ValueError, "fill in namespace"):
            load_function_config(shipped)


if __name__ == "__main__":
    unittest.main()
