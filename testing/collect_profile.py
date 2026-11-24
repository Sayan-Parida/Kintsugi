#!/usr/bin/env python3
"""Example collector for an explicitly trusted, controlled lab observer."""
import datetime
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys

def main():
    phase, destination = sys.argv[1:]
    if phase not in ("opening", "closing"):
        raise ValueError("Invalid phase")
    os.umask(0o077)
    template = json.loads(Path(os.environ["KINTSUGI_PROFILE_TEMPLATE"]).read_text())
    destination = Path(destination).resolve()
    nonce = secrets.token_hex(32)
    issued = datetime.datetime.now(datetime.timezone.utc)
    evidence = destination.with_suffix(".evidence.json")
    # Collector uses the authenticated target executable. The profile verifier is separately enrolled.
    subprocess.run(["/run/shirudo/runtime/usr/bin/shirudo", "attest", "--nonce=" + nonce,
                    "--output=" + str(evidence)], check=True, timeout=7,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    template.update(evidence=str(evidence), nonce=nonce, window={
        "issued": issued.isoformat(),
        "expires": (issued + datetime.timedelta(seconds=60)).isoformat()})
    temporary = destination.with_suffix(".pending")
    with temporary.open("x") as out:
        json.dump(template, out)
    os.replace(temporary, destination)

if __name__ == "__main__":
    main()
