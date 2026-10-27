#!/usr/bin/env python3
"""Compute the next alpha release and update every versioned public file."""
import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "android-app" / "version.properties"
CHANGELOG = ROOT / "CHANGELOG.md"
FDROID_METADATA = ROOT / "fdroid" / "metadata" / "in.txnsense.app.yml"
FASTLANE_CHANGELOGS = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)-alpha\.(\d+)$")
PROPERTY_RE = re.compile(r"^(versionName|versionCode)=(.*)$", re.MULTILINE)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def parse_version(value):
    match = VERSION_RE.match(value)
    if not match:
        raise SystemExit(f"Unsupported versionName: {value}")
    return tuple(int(part) for part in match.groups())


def read_version(path=VERSION_FILE):
    text = path.read_text(encoding="utf-8")
    values = dict(PROPERTY_RE.findall(text))
    return text, values["versionName"], int(values["versionCode"])


def alpha_tags():
    versions = {}
    for tag in git("tag", "--list", "v*-alpha.*").splitlines():
        try:
            versions[tag] = parse_version(tag.removeprefix("v"))
        except SystemExit:
            continue
    return versions


def tag_code(tag):
    try:
        text = git("show", f"{tag}:android-app/version.properties")
    except subprocess.CalledProcessError:
        return 0
    match = re.search(r"^versionCode=(\d+)$", text, re.MULTILINE)
    return int(match.group(1)) if match else 0


def unreleased_notes(changelog):
    match = re.search(r"(?ms)^## Unreleased[ \t]*\n(.*?)(?=^## |\Z)", changelog)
    if not match:
        raise SystemExit("CHANGELOG.md has no Unreleased section")
    return match.group(1).strip()


def commit_notes(previous_tag):
    revision = f"{previous_tag}..HEAD" if previous_tag else "HEAD"
    subjects = git("log", "--no-merges", "--format=%s", revision).splitlines()
    subjects = [s for s in subjects if s and not s.startswith("chore(release):") and "[skip ci]" not in s]
    return "\n".join(f"- {subject}" for subject in subjects)


def replace_unreleased(changelog, version, notes):
    match = re.search(r"(?ms)^## Unreleased[ \t]*\n(.*?)(?=^## |\Z)", changelog)
    if not match:
        raise SystemExit("CHANGELOG.md has no Unreleased section")
    section = f"## Unreleased\n\n## {version}\n\n{notes}\n\n"
    return changelog[:match.start()] + section + changelog[match.end():]


def fastlane_text(notes):
    plain = " ".join(
        line[2:].strip() if line.startswith("- ") else line.strip()
        for line in notes.splitlines()
        if line.strip()
    )
    return plain if len(plain) <= 500 else plain[:497].rstrip() + "..."


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="print the next version without writing files")
    parser.add_argument("--github-output", help="append version, code and tag to this GITHUB_OUTPUT file")
    args = parser.parse_args()

    version_text, current_name, current_code = read_version()
    tags = alpha_tags()
    latest = max([current_name, *[tag.removeprefix("v") for tag in tags]], key=parse_version)
    major, minor, patch, alpha = parse_version(latest)
    version = f"{major}.{minor}.{patch}-alpha.{alpha + 1}"
    code = max([current_code, *map(tag_code, tags)], default=current_code) + 1

    previous_tag = max(tags, key=lambda tag: parse_version(tag.removeprefix("v")), default=None)
    changelog = CHANGELOG.read_text(encoding="utf-8")
    notes = unreleased_notes(changelog) or commit_notes(previous_tag) or "- Maintenance release."

    print(f"tag=v{version}")
    print(f"version={version}")
    print(f"code={code}")
    if args.dry_run:
        return 0

    updated_version = re.sub(r"(?m)^versionName=.*$", f"versionName={version}", version_text)
    updated_version = re.sub(r"(?m)^versionCode=.*$", f"versionCode={code}", updated_version)
    VERSION_FILE.write_text(updated_version, encoding="utf-8")
    CHANGELOG.write_text(replace_unreleased(changelog, version, notes), encoding="utf-8")

    metadata = FDROID_METADATA.read_text(encoding="utf-8")
    metadata = re.sub(r"(?m)^CurrentVersion:.*$", f"CurrentVersion: {version}", metadata)
    metadata = re.sub(r"(?m)^CurrentVersionCode:.*$", f"CurrentVersionCode: {code}", metadata)
    FDROID_METADATA.write_text(metadata, encoding="utf-8")

    FASTLANE_CHANGELOGS.mkdir(parents=True, exist_ok=True)
    (FASTLANE_CHANGELOGS / f"{code}.txt").write_text(fastlane_text(notes) + "\n", encoding="utf-8")

    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as output:
            output.write(f"tag=v{version}\nversion={version}\ncode={code}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
