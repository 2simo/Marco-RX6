#!/usr/bin/env python3
"""Bundle the latest real MARCO RX6 Blogger covers and post data for offline use."""
import hashlib
import html
import json
import re
import time
from io import BytesIO
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app" / "src" / "main" / "assets"
COVER_DIR = ASSET_ROOT / "offline_covers"
FEED_URL = "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100"
MAX_COVERS = 20
MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024


def first_image_url(content):
    match = re.search(r"(?is)<img[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']", content or "")
    if not match:
        return ""
    url = html.unescape(match.group(1)).strip()
    return url if url.startswith(("https://", "http://")) else ""


def largest_blogger_variant(url):
    return re.sub(
        r"=(?:s\d+(?:-[a-z0-9-]+)?|w\d+-h\d+(?:-[a-z0-9-]+)?)$",
        "=s1600",
        url,
        flags=re.IGNORECASE,
    )


def fetch_bytes(url):
    request = Request(url, headers={
        "User-Agent": "Mozilla/5.0 (compatible; MARCO-RX6-Offline-Catalog/1.0)",
        "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
    })
    with urlopen(request, timeout=25) as response:
        content_type = response.headers.get("Content-Type", "").lower()
        if not content_type.startswith("image/"):
            raise ValueError("response is not an image")
        data = response.read(MAX_DOWNLOAD_BYTES + 1)
    if not data or len(data) > MAX_DOWNLOAD_BYTES:
        raise ValueError("image is empty or exceeds the size limit")
    return data


def main():
    COVER_DIR.mkdir(parents=True, exist_ok=True)
    for old in COVER_DIR.glob("*"):
        if old.is_file():
            old.unlink()

    request = Request(FEED_URL, headers={
        "User-Agent": "Mozilla/5.0 (compatible; MARCO-RX6-Offline-Catalog/1.0)",
        "Accept": "application/json",
    })
    last_error = None
    feed = None
    for attempt in range(3):
        try:
            with urlopen(request, timeout=30) as response:
                feed = json.loads(response.read().decode("utf-8"))
            break
        except (HTTPError, URLError, TimeoutError, ValueError) as exc:
            last_error = exc
            if attempt < 2:
                time.sleep(2 ** attempt)
    if feed is None:
        raise SystemExit(f"Could not read MARCO RX6 Blogger feed: {last_error}")

    entries = feed.get("feed", {}).get("entry", [])
    bundled_entries = []
    total_bytes = 0

    for entry in entries:
        if len(bundled_entries) >= MAX_COVERS:
            break
        title = (entry.get("title") or {}).get("$t", "").strip()
        content = (entry.get("content") or entry.get("summary") or {}).get("$t", "")
        cover_url = first_image_url(content)
        links = entry.get("link", [])
        post_url = next((item.get("href", "") for item in links
                         if item.get("rel") == "alternate"
                         and item.get("href", "").startswith("https://")), "")
        if not title or not cover_url or not post_url:
            continue

        try:
            raw = fetch_bytes(largest_blogger_variant(cover_url))
            with Image.open(BytesIO(raw)) as source:
                image = ImageOps.exif_transpose(source).convert("RGB")
                image.thumbnail((1280, 1700), Image.Resampling.LANCZOS)
                output = BytesIO()
                image.save(output, format="WEBP", quality=92, method=5)
                encoded = output.getvalue()
            if not encoded:
                raise ValueError("image conversion produced no data")
        except Exception as exc:
            print(f"Skipping cover for {title!r}: {exc}")
            continue

        filename = hashlib.sha256(cover_url.encode("utf-8")).hexdigest() + ".webp"
        (COVER_DIR / filename).write_bytes(encoded)
        total_bytes += len(encoded)
        bundled_entries.append(entry)
        print(f"Bundled {len(bundled_entries):02d}: {title} ({len(encoded):,} bytes)")

    if not bundled_entries:
        raise SystemExit("No usable game covers were found in the MARCO RX6 feed.")

    (ASSET_ROOT / "offline_catalog.json").write_text(
        json.dumps(bundled_entries, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"Offline catalog: {len(bundled_entries)} real posts")
    print(f"Bundled cover payload: {total_bytes:,} bytes")
    if len(bundled_entries) < 15:
        print("NOTICE: Fewer than 15 posts currently have downloadable cover art; no filler assets were added.")


if __name__ == "__main__":
    main()
