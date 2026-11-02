#!/usr/bin/env python3
"""Bundle real MARCO RX6 game posts and their images for offline reading."""
import hashlib, html, json, re, time
from io import BytesIO
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app" / "src" / "main" / "assets"
IMAGE_DIR = ASSET_ROOT / "offline_images"
FEED_URL = "https://marcorx6.blogspot.com/feeds/posts/default?alt=json&max-results=100"
MAX_POSTS, MAX_IMAGES_PER_POST, MAX_TOTAL_IMAGES = 20, 12, 160
MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
MAX_TOTAL_IMAGE_BYTES = 11 * 1024 * 1024
IMAGE_TAG = re.compile(r"(?is)<img\b[^>]*>")
IMAGE_SOURCE = re.compile(r"""(?is)(?:^|\s)(?:src|data-src)\s*=\s*(["'])(.*?)\1""")

def image_refs(content):
    refs = []
    for tag in IMAGE_TAG.finditer(content or ""):
        attr = IMAGE_SOURCE.search(tag.group(0))
        if attr:
            raw = attr.group(2).strip()
            url = html.unescape(raw)
            if url.startswith(("https://", "http://")):
                refs.append((raw, url))
    return refs

def largest_blogger_variant(url):
    return re.sub(r"=(?:s\d+(?:-[a-z0-9-]+)?|w\d+-h\d+(?:-[a-z0-9-]+)?)$", "=s2000", url, flags=re.I)

def fetch_bytes(url):
    req = Request(url, headers={"User-Agent":"Mozilla/5.0 (compatible; MARCO-RX6-Offline-Reader/1.0)","Accept":"image/avif,image/webp,image/apng,image/*,*/*;q=0.8"})
    with urlopen(req, timeout=25) as response:
        if not response.headers.get("Content-Type","").lower().startswith("image/"):
            raise ValueError("response is not an image")
        data = response.read(MAX_DOWNLOAD_BYTES + 1)
    if not data or len(data) > MAX_DOWNLOAD_BYTES:
        raise ValueError("image is empty or exceeds the size limit")
    return data

def main():
    IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    for old in IMAGE_DIR.glob("*"):
        if old.is_file(): old.unlink()
    req = Request(FEED_URL, headers={"User-Agent":"Mozilla/5.0 (compatible; MARCO-RX6-Offline-Reader/1.0)","Accept":"application/json"})
    feed, last_error = None, None
    for attempt in range(3):
        try:
            with urlopen(req, timeout=30) as response:
                feed = json.loads(response.read().decode("utf-8"))
            break
        except (HTTPError, URLError, TimeoutError, ValueError) as exc:
            last_error = exc
            if attempt < 2: time.sleep(2 ** attempt)
    if feed is None: raise SystemExit(f"Could not read MARCO RX6 Blogger feed: {last_error}")

    bundled, image_assets, total_bytes, total_images = [], {}, 0, 0
    non_game = re.compile(r"(?i)\b(youtube|netflix|temperature|explorer)\b")
    for entry in feed.get("feed", {}).get("entry", []):
        if len(bundled) >= MAX_POSTS: break
        title = (entry.get("title") or {}).get("$t", "").strip()
        if not title or non_game.search(title): continue
        content = (entry.get("content") or entry.get("summary") or {}).get("$t", "")
        refs = image_refs(content)
        links = entry.get("link", [])
        post_url = next((x.get("href","") for x in links if x.get("rel")=="alternate" and x.get("href","").startswith("https://")), "")
        if not refs or not post_url: continue

        successful = 0
        for raw_url, image_url in refs[:MAX_IMAGES_PER_POST]:
            if image_url in image_assets:
                successful += 1
                continue
            if total_images >= MAX_TOTAL_IMAGES or total_bytes >= MAX_TOTAL_IMAGE_BYTES: break
            try:
                raw = fetch_bytes(largest_blogger_variant(image_url))
                with Image.open(BytesIO(raw)) as source:
                    image = ImageOps.exif_transpose(source).convert("RGB")
                    image.thumbnail((1800, 2400), Image.Resampling.LANCZOS)
                    output = BytesIO()
                    image.save(output, format="WEBP", quality=94, method=5)
                    encoded = output.getvalue()
                if not encoded: raise ValueError("image conversion produced no data")
                if total_bytes + len(encoded) > MAX_TOTAL_IMAGE_BYTES:
                    print(f"Image budget reached before {title!r}; remaining images stay online-only.")
                    break
                filename = hashlib.sha256(image_url.encode("utf-8")).hexdigest() + ".webp"
                (IMAGE_DIR / filename).write_bytes(encoded)
                image_assets[image_url] = filename
                total_bytes += len(encoded)
                total_images += 1
                successful += 1
            except Exception as exc:
                print(f"Skipping image for {title!r}: {exc}")

        if refs[0][1] not in image_assets: continue
        offline_html = content
        for raw_url, image_url in refs:
            filename = image_assets.get(image_url)
            if not filename: continue
            local_uri = "file:///android_asset/offline_images/" + filename
            offline_html = offline_html.replace(raw_url, local_uri)
            offline_html = offline_html.replace(html.escape(image_url, quote=True), local_uri)
            offline_html = offline_html.replace(image_url, local_uri)
        entry["offlineHtml"] = offline_html
        bundled.append(entry)
        print(f"Offline post {len(bundled):02d}: {title} ({successful} images)")

    if not bundled: raise SystemExit("No usable game posts with cover art were found in the MARCO RX6 feed.")
    (ASSET_ROOT / "offline_catalog.json").write_text(json.dumps(bundled, ensure_ascii=False, separators=(",",":")), encoding="utf-8")
    print(f"Offline game posts: {len(bundled)}")
    print(f"Offline article images: {total_images}")
    print(f"Bundled image payload: {total_bytes:,} bytes")
    if total_bytes < 7 * 1024 * 1024:
        print("NOTICE: Source posts contain less than 7 MB of usable article images; no artificial padding was added.")

if __name__ == "__main__":
    main()
