#!/usr/bin/env python3
"""Fetch + decrypt AppleTV2,1 iBSS (11D258 / tvOS 7.1.2) into android assets."""
import json, os, struct, sys, urllib.request, zipfile, plistlib

try:
    from Crypto.Cipher import AES
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pycryptodome", "-q"])
    from Crypto.Cipher import AES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEVICE = "AppleTV2,1"
BUILD = "11D258"
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "payloads", "ibss.img3")


class HTTPRangeFile:
    def __init__(self, url):
        self.url = url
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req, timeout=60) as r:
            self.size = int(r.headers["Content-Length"])
        self.pos = 0

    def seek(self, offset, whence=0):
        if whence == 0:
            self.pos = offset
        elif whence == 1:
            self.pos += offset
        elif whence == 2:
            self.pos = self.size + offset
        return self.pos

    def tell(self):
        return self.pos

    def seekable(self):
        return True

    def read(self, n=-1):
        end = (self.size - 1) if n < 0 else (min(self.pos + n, self.size) - 1)
        if end < self.pos:
            return b""
        req = urllib.request.Request(self.url, headers={"Range": f"bytes={self.pos}-{end}"})
        with urllib.request.urlopen(req, timeout=180) as r:
            data = r.read()
        self.pos += len(data)
        return data


def find_path(node, target):
    if isinstance(node, dict):
        for k, v in node.items():
            if k.lower() == target.lower() and isinstance(v, dict):
                info = v.get("Info")
                if isinstance(info, dict) and "Path" in info:
                    return info["Path"]
            found = find_path(v, target)
            if found:
                return found
    elif isinstance(node, list):
        for item in node:
            found = find_path(item, target)
            if found:
                return found
    return None


def main():
    keys_path = os.path.join(ROOT, "keys", DEVICE, f"{DEVICE}_{BUILD}.keys")
    with open(keys_path, "rb") as f:
        keys = plistlib.load(f)
    iv = bytes.fromhex(keys["iBSS"][0])
    key = bytes.fromhex(keys["iBSS"][1])

    with urllib.request.urlopen(f"https://api.ipsw.me/v4/device/{DEVICE}?type=ipsw", timeout=30) as r:
        data = json.load(r)
    url = next(fw["url"] for fw in data["firmwares"] if fw["buildid"] == BUILD)
    print("url", url)

    zf = zipfile.ZipFile(HTTPRangeFile(url))
    manifest = plistlib.loads(zf.read("BuildManifest.plist"))
    path = find_path(manifest, "iBSS")
    print("component", path)
    enc = zf.read(path)
    print("encrypted", len(enc))

    off = 20
    out = None
    while off + 12 <= len(enc):
        tag_total = struct.unpack_from("<I", enc, off + 4)[0]
        tag_name = enc[off:off + 4]
        tag_data = struct.unpack_from("<I", enc, off + 8)[0]
        if tag_name == b"ATAD":
            payload = enc[off + 12:off + 12 + tag_data]
            pad = (16 - (len(payload) % 16)) % 16
            dec = AES.new(key, AES.MODE_CBC, iv).decrypt(payload + b"\x00" * pad)[:len(payload)]
            out = enc[:off + 12] + dec + enc[off + 12 + tag_data:]
            break
        if tag_total < 12:
            break
        off += tag_total
    if not out:
        raise SystemExit("no DATA tag")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "wb").write(out)
    print("wrote", OUT, len(out))


if __name__ == "__main__":
    main()
