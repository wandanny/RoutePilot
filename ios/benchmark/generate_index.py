"""Generate the compact offline address index for Taiping/Kamunting/Simpang.

Reads OSM data from a public Overpass mirror and writes a JSONL file where each
line is: {"a":"normalized address","n":"name","p":"postcode","y":lat,"x":lon}

Usage: python ios/benchmark/generate_index.py
"""
import json
import re
import time
import urllib.parse
import urllib.request

BBOX = "4.78,100.66,4.94,100.84"
ENDPOINT = "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
OUT = r"ios/data/taiping-addresses.jsonl"
VERSION_OUT = r"ios/data/taiping-addresses.version"
UTC = "2026-11-26T00:00:00Z"

QUERY = f"""
[out:json][timeout:240];
(
  nwr["addr:housenumber"]["addr:street"]({BBOX});
  nwr["shop"]["name"]["addr:street"]({BBOX});
  nwr["amenity"]["name"]["addr:street"]({BBOX});
  nwr["office"]["name"]["addr:street"]({BBOX});
);
out center;
"""


def normalize(text):
    text = text.lower()
    text = re.sub(r"[^a-z0-9 ]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def fetch():
    data = urllib.parse.urlencode({"data": QUERY}).encode("utf-8")
    for attempt in range(6):
        request = urllib.request.Request(
            ENDPOINT, data=data, headers={"User-Agent": "RoutePilot-index-builder/1.0"}
        )
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception as error:
            print(f"attempt {attempt + 1} failed: {error}")
            time.sleep(15 * (attempt + 1))
    raise RuntimeError("Overpass mirror unavailable after retries")


def main():
    payload = fetch()
    entries = {}
    for element in payload.get("elements", []):
        tags = element.get("tags") or {}
        center = element.get("center") or {}
        lat = element.get("lat", center.get("lat"))
        lon = element.get("lon", center.get("lon"))
        if lat is None or lon is None:
            continue
        house = tags.get("addr:housenumber", "")
        street = tags.get("addr:street", "")
        name = tags.get("name", "")
        postcode = tags.get("addr:postcode", "")
        address = normalize(f"{house} {street}".strip())
        if not address and not name:
            continue
        if postcode and not postcode.startswith("34"):
            postcode = ""  # drop obvious outliers such as 40400
        key = (address, name)
        if key in entries:
            continue
        entries[key] = {
            "a": address,
            "n": normalize(name),
            "p": postcode,
            "y": round(float(lat), 6),
            "x": round(float(lon), 6),
        }

    with open(OUT, "w", encoding="utf-8", newline="\n") as handle:
        for entry in entries.values():
            handle.write(json.dumps(entry, separators=(",", ":"), ensure_ascii=False) + "\n")
    with open(VERSION_OUT, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(UTC + "\n")
    print(f"wrote {len(entries)} entries to {OUT}")


if __name__ == "__main__":
    main()
