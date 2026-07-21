from __future__ import annotations

import argparse
import json
import os
import posixpath
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath


def clean_name(name: str) -> str:
    return name.replace("\\", "/").lstrip("./")


def parent_string(path: str) -> str:
    parent = str(PurePosixPath(path).parent)
    return "" if parent == "." else parent


def choose_gradle_version(agp_version: str) -> str:
    try:
        numbers = tuple(int(part) for part in agp_version.split(".")[:2])
    except Exception:
        return "8.13"

    if numbers <= (8, 7):
        return "8.9"
    if numbers <= (8, 9):
        return "8.11.1"
    if numbers == (8, 10):
        return "8.11.1"
    return "8.13"


def read_member(archive: zipfile.ZipFile, member: str) -> str:
    try:
        return archive.read(member).decode("utf-8", errors="replace")
    except Exception:
        return ""


def find_first(names: list[str], basenames: tuple[str, ...]) -> str | None:
    for name in sorted(names, key=lambda value: (value.count("/"), len(value), value)):
        if PurePosixPath(name).name.lower() in basenames:
            return name
    return None


def find_android(names: list[str], archive: zipfile.ZipFile) -> dict | None:
    settings = find_first(names, ("settings.gradle", "settings.gradle.kts"))
    if not settings:
        return None

    root = parent_string(settings)
    prefix = f"{root}/" if root else ""

    app_build_candidates = [
        name for name in names
        if name in (
            f"{prefix}app/build.gradle",
            f"{prefix}app/build.gradle.kts",
        )
    ]
    if not app_build_candidates:
        return None

    build_files = [
        name for name in names
        if name.startswith(prefix)
        and PurePosixPath(name).name.lower() in (
            "build.gradle",
            "build.gradle.kts",
            "libs.versions.toml",
        )
    ]
    build_text = "\n".join(read_member(archive, name) for name in build_files)

    agp_patterns = [
        r'com\.android\.application["\']?\s+version\s+["\']([0-9.]+)',
        r'com\.android\.application\s*=\s*\{[^}]*version\s*=\s*["\']([0-9.]+)',
        r'com\.android\.tools\.build:gradle:([0-9.]+)',
        r'agp\s*=\s*["\']([0-9.]+)',
    ]
    agp_version = ""
    for pattern in agp_patterns:
        match = re.search(pattern, build_text, flags=re.IGNORECASE | re.DOTALL)
        if match:
            agp_version = match.group(1)
            break
    if not agp_version:
        agp_version = "8.11.1"

    compile_match = re.search(
        r'compileSdk(?:Version)?\s*(?:=|\s)\s*["\']?([0-9]+)',
        build_text,
        flags=re.IGNORECASE,
    )
    compile_sdk = compile_match.group(1) if compile_match else "35"

    extension_match = re.search(
        r'compileSdkExtension\s*(?:=|\s)\s*([0-9]+)',
        build_text,
        flags=re.IGNORECASE,
    )
    extension = extension_match.group(1) if extension_match else "0"

    build_tools_match = re.search(
        r'buildToolsVersion\s*(?:=|\s)\s*["\']([0-9.]+)',
        build_text,
        flags=re.IGNORECASE,
    )
    build_tools = (
        build_tools_match.group(1)
        if build_tools_match
        else f"{compile_sdk}.0.0"
    )

    return {
        "project_type": "android",
        "project_root": root,
        "agp_version": agp_version,
        "gradle_version": choose_gradle_version(agp_version),
        "compile_sdk": compile_sdk,
        "sdk_extension": extension,
        "build_tools": build_tools,
        "entrypoint": "",
    }


def common_parent(paths: list[str]) -> str:
    if not paths:
        return ""
    split_paths = [clean_name(path).split("/")[:-1] for path in paths]
    common: list[str] = []
    for parts in zip(*split_paths):
        if len(set(parts)) == 1:
            common.append(parts[0])
        else:
            break
    return "/".join(common)


def find_windows_python(names: list[str], archive: zipfile.ZipFile) -> dict | None:
    requirement_files = [
        name for name in names
        if PurePosixPath(name).name.lower() == "requirements.txt"
    ]
    entry_candidates = [
        name for name in names
        if PurePosixPath(name).name.lower() in (
            "app.py",
            "main.py",
            "gui.py",
            "desktop_app.py",
        )
    ]
    spec_files = [
        name for name in names
        if name.lower().endswith(".spec")
    ]

    if not entry_candidates and not spec_files:
        return None

    selected_paths = requirement_files + entry_candidates + spec_files
    root = common_parent(selected_paths)
    prefix = f"{root}/" if root else ""

    entrypoint = ""
    for preferred in ("app.py", "main.py", "gui.py", "desktop_app.py"):
        matches = [
            name for name in entry_candidates
            if PurePosixPath(name).name.lower() == preferred
            and name.startswith(prefix)
        ]
        if matches:
            entrypoint = sorted(matches, key=lambda value: (value.count("/"), len(value)))[0]
            break

    if entrypoint and prefix and entrypoint.startswith(prefix):
        entrypoint = entrypoint[len(prefix):]

    spec_file = ""
    matching_specs = [name for name in spec_files if name.startswith(prefix)]
    if matching_specs:
        spec_file = sorted(matching_specs, key=lambda value: (value.count("/"), len(value)))[0]
        if prefix and spec_file.startswith(prefix):
            spec_file = spec_file[len(prefix):]

    return {
        "project_type": "windows_python",
        "project_root": root,
        "agp_version": "",
        "gradle_version": "",
        "compile_sdk": "",
        "sdk_extension": "",
        "build_tools": "",
        "entrypoint": entrypoint,
        "spec_file": spec_file,
    }


def find_prebuilt(names: list[str]) -> dict | None:
    artifacts = [
        name for name in names
        if name.lower().endswith((".apk", ".exe", ".msi"))
        and "__macosx/" not in name.lower()
    ]
    if not artifacts:
        return None
    return {
        "project_type": "prebuilt",
        "project_root": "",
        "agp_version": "",
        "gradle_version": "",
        "compile_sdk": "",
        "sdk_extension": "",
        "build_tools": "",
        "entrypoint": "",
        "artifacts": artifacts,
    }


def write_outputs(data: dict, output_path: str | None) -> None:
    normalized = {
        "project_type": data.get("project_type", ""),
        "project_root": data.get("project_root", ""),
        "agp_version": data.get("agp_version", ""),
        "gradle_version": data.get("gradle_version", ""),
        "compile_sdk": data.get("compile_sdk", ""),
        "sdk_extension": data.get("sdk_extension", ""),
        "build_tools": data.get("build_tools", ""),
        "entrypoint": data.get("entrypoint", ""),
        "spec_file": data.get("spec_file", ""),
    }

    if output_path:
        with open(output_path, "a", encoding="utf-8") as output:
            for key, value in normalized.items():
                output.write(f"{key}={value}\n")

    print(json.dumps({**data, **normalized}, indent=2))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("zip_file")
    parser.add_argument("--github-output", default=os.environ.get("GITHUB_OUTPUT"))
    arguments = parser.parse_args()

    zip_path = Path(arguments.zip_file)
    if not zip_path.is_file():
        print(f"Build input not found: {zip_path}", file=sys.stderr)
        return 2

    with zipfile.ZipFile(zip_path) as archive:
        names = [
            clean_name(name)
            for name in archive.namelist()
            if not name.endswith("/")
            and "__macosx/" not in clean_name(name).lower()
        ]

        project = (
            find_android(names, archive)
            or find_windows_python(names, archive)
            or find_prebuilt(names)
        )

    if not project:
        print(
            "Unknown project type. The ZIP must contain an Android Gradle project, "
            "a Python Windows app, or a prebuilt APK/EXE.",
            file=sys.stderr,
        )
        return 3

    write_outputs(project, arguments.github_output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
