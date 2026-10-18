"""Routing-panel translations and component-preserving pipe drops."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
TEXT = {
    "routing": "Pipe Routing", "short.down": "Dn", "short.up": "Up", "short.north": "N", "short.south": "S", "short.west": "W", "short.east": "E",
    "connection": "Connection", "connection.state": "%s: %s", "connection.none": "Closed / unconnected", "connection.pipe": "Pipe link",
    "connection.insert": "Insert into neighbour", "connection.extract": "Extract from neighbour",
    "endpoint.help": "Click to cycle insert, extract and disabled. Filters affect inventory/tank connections; pipe links pass through.",
    "mode.0": "No filter", "mode.1": "Allow list", "mode.2": "Block list",
    "mode.help": "No filter: everything. Allow: listed resources only. Block: everything except listed resources.",
    "match.type": "Match type", "match.exact": "Match components",
    "match.help": "Type ignores names, damage and other components. Components requires an exact resource match.",
    "clear": "Clear", "clear.help": "Clear this face's reference list; preserve its mode and priority.",
    "priority": "Destination priority: %s", "priority.help": "Higher priorities fill first. Full or filtered destinations fall back to lower priorities. Equal priorities rotate fairly.",
    "empty_allow": "Empty allow list: blocked", "sample.item": "Four item references", "sample.fluid": "Use filled containers",
    "ghost.help": "Left-click with a carried item to copy it; right-click to clear. Shift-click inventory to add a reference.",
    "sample.help": "References never consume or return items. Fluid filters read the liquid from a filled bucket or tank.",
}


def generate(language):
    language.update({"gui.powerfactor.pipe." + key: value for key, value in TEXT.items()})
    language["message.powerfactor.pipe.invalid_sample"] = "That reference does not contain a resource this pipe can carry."
    for name in ("item_pipe", "fluid_pipe", "steam_pipe"):
        path = ROOT / f"data/powerfactor/loot_table/blocks/{name}.json"
        path.write_text(json.dumps({"type": "minecraft:block", "random_sequence": f"powerfactor:blocks/{name}", "pools": [
            {"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [
                {"type": "minecraft:item", "name": "powerfactor:" + name, "functions": [
                    {"function": "minecraft:copy_components", "source": "block_entity", "include": ["powerfactor:pipe_settings"]}]}]}]}, indent=2) + "\n")
    key = "handbook.powerfactor.logistics.text"
    if key in language and "Pipe Routing" not in language[key]:
        language[key] += "\n\nSneak-use a pipe with an empty hand to open Pipe Routing. Each world-direction face has four ghost references, an allow/block mode, exact component matching and destination priority. Filled containers select fluid references. High priorities fill first; full targets overflow to lower priorities. Filters affect endpoints, while pipe-to-pipe links pass through. Breaking or wrench-packing a pipe preserves its routing settings."


if __name__ == "__main__":
    lang_path = ROOT / "assets/powerfactor/lang/en_us.json"
    language = json.loads(lang_path.read_text())
    generate(language)
    from text_style import normalize
    normalize(language)
    lang_path.write_text(json.dumps(language, indent=2, ensure_ascii=False) + "\n")
    print("Generated routing translations and component-preserving drops for three pipe types.")
