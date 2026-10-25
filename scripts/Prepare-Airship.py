"""Prepare the one inspected public fixture; this is not a general NBT editor.

Usage: python scripts/Prepare-Airship.py path/to/air_ship_v10.nbt [output.nbt]
Only two mob entities are removed. Every other uncompressed NBT byte is retained.
"""
import gzip
import hashlib
from pathlib import Path
import struct
import sys

ORIGINAL = "787e4586fcb7ac51239b009368444d528933a7ed4b3624dd6598ae83e423d9c6"
PREPARED = "f2d16fb6b5343cd872f0d02ec9d247cb11a48d21ea6be950963a627f297beaa2"


class Reader:
    def __init__(self, data):
        self.data, self.offset, self.entities = data, 0, None

    def take(self, count):
        if count < 0 or self.offset + count > len(self.data):
            raise ValueError("Truncated NBT")
        start = self.offset
        self.offset += count
        return self.data[start:self.offset]

    def number(self, kind):
        return struct.unpack(">" + kind, self.take(struct.calcsize(">" + kind)))[0]

    def text(self):
        return self.take(self.number("H")).decode("utf-8")

    def payload(self, kind, root=False, capture=False):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return self.take(self.number("i"))
        if kind == 8:
            return self.text()
        if kind == 9:
            start = self.offset
            child, count = self.number("B"), self.number("i")
            if not 0 <= count <= len(self.data):
                raise ValueError("Invalid NBT list")
            values, pieces = [], []
            for _ in range(count):
                item_start = self.offset
                value = self.payload(child)
                values.append(value)
                pieces.append((value, self.data[item_start:self.offset]))
            if capture:
                if child != 10:
                    raise ValueError("Expected entity compounds")
                self.entities = (start, self.offset, pieces)
            return values
        if kind == 10:
            values = {}
            while (child := self.number("B")) != 0:
                name = self.text()
                values[name] = self.payload(child, capture=root and name == "entities")
            return values
        if kind in (11, 12):
            return self.take(self.number("i") * (4 if kind == 11 else 8))
        raise ValueError("Unknown NBT type")


def prepare(source, destination):
    original = source.read_bytes()
    if hashlib.sha256(original).hexdigest() != ORIGINAL:
        raise ValueError("Input is not the inspected Patrik airship v10 download; refusing to alter it")
    raw = gzip.decompress(original)
    reader = Reader(raw)
    if reader.number("B") != 10:
        raise ValueError("Expected compound root")
    reader.text()
    reader.payload(10, root=True)
    if reader.offset != len(raw) or reader.entities is None:
        raise ValueError("Invalid fixture root")
    start, end, pieces = reader.entities
    ids = [value.get("nbt", {}).get("id") for value, _ in pieces]
    if sorted(ids) != sorted(["create:super_glue", "create:super_glue", "minecraft:creeper", "minecraft:bat"]):
        raise ValueError("Unexpected entity set")
    glue = [piece for value, piece in pieces if value["nbt"]["id"] == "create:super_glue"]
    replacement = struct.pack(">Bi", 10, 2) + b"".join(glue)
    output = bytearray(gzip.compress(raw[:start] + replacement + raw[end:], mtime=0))
    output[9] = 10  # Canonical OS header used by the inspected Windows fixture.
    if hashlib.sha256(output).hexdigest() != PREPARED:
        raise ValueError("Prepared file differs from the tested fixture; output was not written")
    if source.resolve() == destination.resolve():
        raise ValueError("Preserve the original download; choose a different output path")
    if destination.exists() and destination.read_bytes() != output:
        raise ValueError("Destination exists with different contents; choose another path")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(output)
    print(f"Prepared {destination.name}: two glue entities retained; creeper and bat removed.")
    print(f"SHA-256 {PREPARED}")


if __name__ == "__main__":
    if not 2 <= len(sys.argv) <= 3:
        raise SystemExit(__doc__)
    output = Path(sys.argv[2]) if len(sys.argv) == 3 else Path(__file__).resolve().parent.parent / "reports/airship-fixture/air_ship_v10-hololith-test.nbt"
    prepare(Path(sys.argv[1]), output)
