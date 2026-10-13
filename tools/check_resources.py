"""Check generated model references, UVs and animation sheets. Run after both generators."""
import json
import re
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/powerfactor"


def check():
    errors = []
    java = (ROOT.parent / "java/com/powerfactor/registry/ModBlocks.java").read_text()
    registered = set(re.findall(r'(?:register(?:Simple)?Block|steamCasing)\("([a-z0-9_]+)"', java))
    recipes = (ROOT.parent / "java/com/powerfactor/machine/RecipeMachine.java").read_text()
    registered.update(re.findall(r'\b[A-Z_]+\("([a-z0-9_]+)",', recipes))
    for block in sorted(registered):
        for directory in ("blockstates", "items"):
            if not (ASSETS / directory / (block + ".json")).is_file():
                errors.append(f"Registered block {block}: missing {directory} definition")
    json_files = list(ROOT.rglob("*.json"))
    for path in json_files:
        try:
            data = json.loads(path.read_text())
        except (ValueError, OSError) as error:
            errors.append(f"{path.relative_to(ROOT)}: {error}")
            continue
        if "models" in path.parts:
            for texture in data.get("textures", {}).values():
                if texture.startswith("powerfactor:") and not (ASSETS / "textures" / (texture.split(":", 1)[1] + ".png")).is_file():
                    errors.append(f"{path.name}: missing texture {texture}")
            parent = data.get("parent", "")
            if parent.startswith("powerfactor:") and not (ASSETS / "models" / (parent.split(":", 1)[1] + ".json")).is_file():
                errors.append(f"{path.name}: missing parent {parent}")
            for element in data.get("elements", []):
                if any(a > b for a, b in zip(element["from"], element["to"])):
                    errors.append(f"{path.name}: inverted element bounds")
                for face in element.get("faces", {}).values():
                    if any(value < 0 or value > 16 for value in face.get("uv", [])):
                        errors.append(f"{path.name}: UV outside atlas tile")
        if "blockstates" in path.parts:
            def refs(value):
                if isinstance(value, dict):
                    if isinstance(value.get("model"), str):
                        yield value["model"]
                    for child in value.values():
                        yield from refs(child)
                elif isinstance(value, list):
                    for child in value:
                        yield from refs(child)
            for model in refs(data):
                if model.startswith("powerfactor:") and not (ASSETS / "models" / (model.split(":", 1)[1] + ".json")).is_file():
                    errors.append(f"{path.name}: missing model {model}")
        if path.parent == ASSETS / "equipment":
            for layer, textures in data.get("layers", {}).items():
                for texture in textures:
                    reference = texture.get("texture", "")
                    if reference.startswith("powerfactor:"):
                        png = ASSETS / "textures/entity/equipment" / layer / (reference.split(":", 1)[1] + ".png")
                        if not png.is_file(): errors.append(f"{path.name}: missing equipment texture {reference} for {layer}")
    animations = list(ASSETS.rglob("*.png.mcmeta"))
    for path in animations:
        data = json.loads(path.read_text()).get("animation", {})
        png = path.with_suffix("")
        with Image.open(png) as image:
            w = data.get("width", min(image.size))
            h = data.get("height", min(image.size))
            if w <= 0 or h <= 0 or image.width % w or image.height % h:
                errors.append(f"{path.name}: invalid frame dimensions")
            if data.get("frametime", 1) <= 0:
                errors.append(f"{path.name}: non-positive frame time")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"Validated {len(json_files)} JSON resources and {len(animations)} animation sheets")


if __name__ == "__main__":
    check()
