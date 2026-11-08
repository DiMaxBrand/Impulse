#!/usr/bin/env python3
"""CI fallback for a failing Gradle browser-download redirect; keep the wrapper's version."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def download(url: str, destination: Path, accept: str) -> None:
    subprocess.run([
        "curl", "--fail", "--location", "--silent", "--show-error",
        "--retry", "3", "--retry-all-errors", "--connect-timeout", "20",
        "--max-time", "180", "--header", f"Accept: {accept}",
        "--output", str(destination), url,
    ], check=True)


def prepare(properties: Path, directory: Path) -> None:
    contents = properties.read_text()
    match = re.search(r"^distributionUrl=.*?/gradle-([0-9][0-9A-Za-z.+-]*)-bin\.zip\s*$", contents, re.MULTILINE)
    if match is None:
        raise ValueError("Expected a versioned Gradle binary distribution in wrapper properties")
    version = match.group(1)
    directory.mkdir(parents=True, exist_ok=True)
    metadata = directory / "gradle-release.json"
    download(f"https://api.github.com/repos/gradle/gradle-distributions/releases/tags/v{version}",
             metadata, "application/vnd.github+json")
    name = f"gradle-{version}-bin.zip"
    asset = next(item for item in json.loads(metadata.read_text())["assets"] if item["name"] == name)
    digest = asset.get("digest", "")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("Official Gradle release does not supply a SHA-256 digest")
    expected = digest.removeprefix("sha256:")
    pinned = re.search(r"^distributionSha256Sum=(\S+)\s*$", contents, re.MULTILINE)
    if pinned and pinned.group(1).lower() != expected:
        raise ValueError("Official release digest differs from the wrapper's pinned checksum")
    if not asset["url"].startswith("https://api.github.com/repos/gradle/gradle-distributions/releases/assets/"):
        raise ValueError("Unexpected Gradle release asset API URL")
    archive = directory / name
    download(asset["url"], archive, "application/octet-stream")
    with archive.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != expected:
        archive.unlink()
        raise ValueError("Downloaded Gradle distribution checksum mismatch")
    # CI checkout only: wrapper still installs and runs Gradle itself, now from a verified ZIP.
    contents = re.sub(r"^distributionUrl=.*$", "distributionUrl=" + archive.resolve().as_uri(), contents, flags=re.MULTILINE)
    if pinned is None:
        contents = contents.rstrip() + f"\ndistributionSha256Sum={expected}\n"
    properties.write_text(contents)
    print(f"Prepared Gradle {version} via official asset API; SHA-256 verified.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--properties", type=Path, default=Path("gradle/wrapper/gradle-wrapper.properties"))
    parser.add_argument("--directory", type=Path,
                        default=Path(os.environ.get("RUNNER_TEMP", tempfile.gettempdir())) / "impulse-gradle-bootstrap")
    args = parser.parse_args()
    prepare(args.properties, args.directory)
