"""Connection and queue details from config/settings.json (same keys as the PowerShell, Java, Node and C# boilerplates)."""

import json
from dataclasses import dataclass

REQUIRED_KEYS = ("namespace", "keyName", "key", "sourceQueue", "destinationQueue")


@dataclass(frozen=True)
class FunctionConfig:
    connection_string: str
    source_queue: str
    destination_queue: str


def load_function_config(path) -> FunctionConfig:
    """Loads the file and stops with one error that names every value that is missing or still a <placeholder>."""
    with open(path, encoding="utf-8") as file:
        settings = json.load(file)
    missing = [key for key in REQUIRED_KEYS
               if not isinstance(settings.get(key), str) or not settings[key].strip() or settings[key].startswith("<")]
    if missing:
        raise ValueError(f"{path}: fill in {', '.join(missing)}")
    return FunctionConfig(
        connection_string=(f"Endpoint=sb://{settings['namespace']}.servicebus.windows.net/;"
                           f"SharedAccessKeyName={settings['keyName']};SharedAccessKey={settings['key']}"),
        source_queue=settings["sourceQueue"],
        destination_queue=settings["destinationQueue"],
    )
