"""Build the shared enriched offline address index for RoutePilot.

Sources:
  - existing OSM-derived index (ios/data/taiping-addresses.jsonl)
  - MCMC postcodes (data.gov.my, CC BY 4.0)
  - GeoNames postal codes and gazetteer (CC BY 4.0)
  - Overture Places bbox extract (CDLA Permissive 2.0 / Apache 2.0)

Writes ios/data/taiping-addresses.jsonl, ios/RoutePilot/taiping-addresses.jsonl
and ios/data/taiping-addresses.version.

Usage: python ios/benchmark/generate_enriched_index.py
"""
import csv
import io
import json
import os
import re
import urllib.request
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA_DIR = os.path.join(ROOT, "ios", "data")
BUNDLE_DIR = os.path.join(ROOT, "ios", "RoutePilot")
OSM_INDEX = os.path.join(DATA_DIR, "taiping-addresses.jsonl")
OUT = OSM_INDEX
BUNDLE_OUT = os.path.join(BUNDLE_DIR, "taiping-addresses.jsonl")
VERSION_OUT = os.path.join(DATA_DIR, "taiping-addresses.version")
OVERTURE_FILE = os.path.join(
    os.environ.get("TEMP", "/tmp"), "opencode", "overture-places.geojsonseq"
)
BBOX = (100.66, 4.78, 100.84, 4.94)
TOWN_CENTRES = {
    "taiping": (4.8525, 100.7420),
    "kamunting": (4.8680, 100.7230),
    "simpang": (4.8500, 100.6900),
}

CERT = None
try:
    import certifi
    CERT = certifi.where()
except Exception:
    pass


def fetch_bytes(url):
    request = urllib.request.Request(url, headers={"User-Agent": "RoutePilot-index-builder/1.0"})
    with urllib.request.urlopen(request, timeout=120, context=None) as response:
        return response.read()


def normalize(text):
    text = (text or "").lower()
    text = re.sub(r"[^a-z0-9 ]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def in_bbox(lat, lon):
    west, south, east, north = BBOX
    return south <= lat <= north and west <= lon <= east


def entry(address="", name="", postcode="", lat=None, lon=None, source=""):
    return {
        "a": normalize(address),
        "n": normalize(name),
        "p": postcode or "",
        "y": round(float(lat), 6),
        "x": round(float(lon), 6),
        "s": source,
    }


def dedupe(entries):
    seen = set()
    result = []
    for item in entries:
        key = (item["a"], item["n"], item["p"], item["y"], item["x"])
        if key in seen:
            continue
        seen.add(key)
        result.append(item)
    return result


def load_osm():
    if not os.path.exists(OSM_INDEX):
        return []
    items = []
    with open(OSM_INDEX, encoding="utf-8") as handle:
        for line in handle:
            try:
                item = json.loads(line)
            except ValueError:
                continue
            item["s"] = "osm"
            items.append(item)
    return items


def mcmc_postcodes():
    raw = fetch_bytes("https://storage.data.gov.my/dictionaries/postcodes.csv").decode("utf-8-sig")
    reader = csv.DictReader(io.StringIO(raw))
    result = []
    for row in reader:
        lowered = {str(k).lower(): v for k, v in row.items()}
        postcode = (lowered.get("postcode") or "").strip()
        city = (lowered.get("city") or "").strip()
        state = (lowered.get("state") or "").strip()
        if not postcode.startswith("34") or "perak" not in state.lower():
            continue
        town = next((name for name in TOWN_CENTRES if name in city.lower()), None)
        if not town:
            continue
        lat, lon = TOWN_CENTRES[town]
        result.append(entry(address=city, name=city, postcode=postcode, lat=lat, lon=lon, source="mcmc"))
    return result


def geonames_postal():
    data = fetch_bytes("https://download.geonames.org/export/zip/MY.zip")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        text = archive.read("MY.txt").decode("utf-8")
    result = []
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) < 9:
            continue
        postcode, place = parts[1], parts[2]
        try:
            lat, lon = float(parts[7]), float(parts[8])
        except ValueError:
            lat = lon = None
        if not postcode.startswith("34"):
            continue
        town = next((name for name in TOWN_CENTRES if name in place.lower()), None)
        if town:
            lat, lon = TOWN_CENTRES[town]
        if lat is None or lon is None or not in_bbox(lat, lon):
            continue
        result.append(entry(address=place, name=place, postcode=postcode, lat=lat, lon=lon, source="geonames_postal"))
    return result


def geonames_gazetteer():
    data = fetch_bytes("https://download.geonames.org/export/dump/MY.zip")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        text = archive.read("MY.txt").decode("utf-8")
    result = []
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) < 8:
            continue
        name, ascii_name, alternates = parts[1], parts[2], parts[3]
        try:
            lat, lon = float(parts[4]), float(parts[5])
        except ValueError:
            continue
        feature_class = parts[6]
        if feature_class not in {"P", "L", "S"} or not in_bbox(lat, lon):
            continue
        primary = normalize(ascii_name or name)
        if primary:
            result.append(entry(name=primary, lat=lat, lon=lon, source="geonames"))
        aliases = []
        for alias in (alternates or "").split(",")[:8]:
            cleaned = normalize(alias)
            if len(cleaned) >= 3 and cleaned != primary:
                aliases.append(cleaned)
        for alias in aliases:
            result.append(entry(name=alias, lat=lat, lon=lon, source="geonames_alias"))
    return result


def overture_places():
    if not os.path.exists(OVERTURE_FILE):
        print("Overture extract not found; skipping")
        return []
    best = {}
    with open(OVERTURE_FILE, encoding="utf-8") as handle:
        for line in handle:
            try:
                feature = json.loads(line)
            except ValueError:
                continue
            properties = feature.get("properties") or {}
            confidence = properties.get("confidence") or 0
            if confidence < 0.7:
                continue
            name = ((properties.get("names") or {}).get("primary") or "").strip()
            addresses = properties.get("addresses") or []
            if not name or not addresses:
                continue
            address = (addresses[0].get("freeform") or "").strip()
            postcode = (addresses[0].get("postcode") or "").strip()
            coordinates = (feature.get("geometry") or {}).get("coordinates") or []
            if len(coordinates) < 2:
                continue
            lon, lat = coordinates[0], coordinates[1]
            if not in_bbox(float(lat), float(lon)):
                continue
            item = entry(address=address, name=name, postcode=postcode,
                         lat=lat, lon=lon, source="overture")
            key = (item["a"], item["n"], item["p"])
            if key not in best or confidence > best[key][0]:
                best[key] = (confidence, item)
    return [item for _, item in best.values()]


def main():
    os.makedirs(DATA_DIR, exist_ok=True)
    os.makedirs(BUNDLE_DIR, exist_ok=True)
    all_entries = []
    batches = [
        ("osm", load_osm),
        ("mcmc", mcmc_postcodes),
        ("geonames_postal", geonames_postal),
        ("geonames", geonames_gazetteer),
        ("overture", overture_places),
    ]
    for label, loader in batches:
        try:
            items = loader()
        except Exception as error:
            print(f"{label}: failed ({error})")
            items = []
        print(f"{label}: {len(items)} entries")
        all_entries.extend(items)

    unique = dedupe(all_entries)
    lines = [json.dumps(item, separators=(",", ":"), ensure_ascii=False) for item in unique]
    text = "\n".join(lines) + "\n"
    for path in (OUT, BUNDLE_OUT):
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
    with open(VERSION_OUT, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("2026-11-26T00:00:00Z\n")
    print(f"wrote {len(unique)} entries, {len(text.encode('utf-8'))} bytes")
    counts = {}
    for item in unique:
        counts[item.get("s", "?")] = counts.get(item.get("s", "?"), 0) + 1
    print("by source:", counts)


if __name__ == "__main__":
    main()
