#!/usr/bin/env python3
"""Contrôle d'un APK avant publication (Bug #9) : signature v2 valide et code natif arm64-v8a.

Un APK qui ne remplit pas ces conditions est refusé par Android avant même Play Protect.
Écrit un rapport JUnit pour le rapport de non-régression.

Usage : check_release_apk.py <app.apk> --junit <rapport.xml> [--build-tools <dossier>]
"""

from __future__ import annotations

import argparse
import glob
import os
import re
import subprocess
import sys
from xml.sax.saxutils import escape

# NR: NR-android-9 essensys-hub/essensys-android-phone-apps#9
TEST_NAME = "release_apk_is_v2_signed_with_arm64_NR_android_9"
MIN_SDK_MAX = 26


def tool(build_tools: str, name: str) -> str:
    path = os.path.join(build_tools, name)
    if not os.path.exists(path):
        raise FileNotFoundError(f"{name} introuvable dans {build_tools}")
    return path


def latest_build_tools() -> str:
    home = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
    dirs = sorted(glob.glob(os.path.join(home, "build-tools", "*")),
                  key=lambda d: [int(x) if x.isdigit() else x for x in re.split(r"[.-]", os.path.basename(d))])
    if not dirs:
        raise FileNotFoundError("aucun build-tools Android trouvé (ANDROID_HOME)")
    return dirs[-1]


def check(signer_out: str, badging_out: str) -> list[str]:
    """Analyse pure des sorties apksigner/aapt2 ; renvoie la liste des problèmes."""
    problems = []
    if not re.search(r"^Verifies\s*$", signer_out, re.M):
        problems.append("la signature ne se vérifie pas")
    if not re.search(r"Verified using v2 scheme \(APK Signature Scheme v2\): true", signer_out):
        problems.append("signature v2 absente (exigée par Android 11+ et Play Protect)")
    native = re.search(r"^native-code:(.*)$", badging_out, re.M)
    if native and "'arm64-v8a'" not in native.group(1):
        problems.append("code natif arm64-v8a absent (la plupart des téléphones)")
    min_sdk = re.search(r"^minSdkVersion:'(\d+)'", badging_out, re.M)
    if not min_sdk:
        problems.append("minSdkVersion illisible")
    elif int(min_sdk.group(1)) > MIN_SDK_MAX:
        problems.append(f"minSdkVersion {min_sdk.group(1)} > {MIN_SDK_MAX} (exclut des appareils supportés)")
    return problems


def junit(name: str, problems: list[str]) -> str:
    failure = f'<failure message="{escape(problems[0])}">{escape(chr(10).join(problems))}</failure>' if problems else ""
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="release-apk" tests="1" failures="{int(bool(problems))}">'
            f'<testcase classname="essensys.release.ApkCheck" name="{name}">{failure}</testcase></testsuite>\n')


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk")
    parser.add_argument("--junit", required=True)
    parser.add_argument("--build-tools")
    args = parser.parse_args(argv)
    if not os.path.isfile(args.apk):
        print(f"KO {args.apk} : fichier introuvable", file=sys.stderr)
        return 2
    bt = args.build_tools or latest_build_tools()
    signer = subprocess.run([tool(bt, "apksigner"), "verify", "--verbose", args.apk], capture_output=True, text=True)
    badging = subprocess.run([tool(bt, "aapt2"), "dump", "badging", args.apk], capture_output=True, text=True)
    problems = check(signer.stdout + signer.stderr, badging.stdout)
    os.makedirs(os.path.dirname(os.path.abspath(args.junit)), exist_ok=True)
    with open(args.junit, "w", encoding="utf-8") as handle:
        handle.write(junit(TEST_NAME, problems))
    print(("OK " if not problems else "KO ") + args.apk + ("" if not problems else " : " + " ; ".join(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
