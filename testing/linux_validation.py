#!/usr/bin/env python3
"""Protected-guest qualification driver. Requires real operator enrollment and collection hooks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import select
import shutil
import subprocess
import sys
import tempfile
import time

def require(condition, message):
    if not condition:
        raise RuntimeError(message)

def executable(value):
    path = Path(value)
    require(path.is_absolute() and path.is_file() and os.access(path, os.X_OK),
            "Executable must be an absolute accessible file")
    return str(path)

def run_hook(hook, *args):
    subprocess.run([hook, *map(str, args)], check=True, timeout=30,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def identity(pid):
    base = Path("/proc") / str(pid)
    def read():
        fields = (base / "stat").read_text().rsplit(")", 1)[1].split()
        return (Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
                os.readlink(base / "ns/pid"), os.readlink(base / "ns/mnt"),
                fields[19], (base / "exe").stat().st_ino)
    first = read()
    require(first == read(), "Process identity changed during observation")
    return first

def assessment(args, fixture, label, case, workspace, expected):
    before = identity(fixture.pid)
    directory = workspace / (label + "-" + case)
    directory.mkdir(mode=0o700)
    opening, closing = directory / "opening.json", directory / "closing.json"
    run_hook(args.provider, "opening", opening)
    command = [args.java, "-jar", args.cli, "assess", "--pid=" + str(fixture.pid),
               "--shield=java-sql-inj", "--digest=" + args.digest, "--profile=" + str(opening),
               "--closing-profile=" + str(closing), "--fixture-pipes", "--json"]
    # FD 3 is established before exec, preserving the existing CLI pipe contract.
    fd = fixture.stdin.fileno()
    shell = ["bash", "-c", 'exec 3>&"$1"; shift; exec "$@"', "kintsugi", str(fd), *command]
    clean_env = dict(os.environ)
    for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        clean_env.pop(name, None)
    with tempfile.TemporaryFile(mode="w+b") as diagnostics:
        process = subprocess.Popen(shell, stdin=fixture.stdout, stdout=subprocess.PIPE,
                                   stderr=diagnostics, pass_fds=(fd,), env=clean_env)
        try:
            deadline = time.monotonic() + 15
            marker = False
            while time.monotonic() < deadline and process.poll() is None:
                diagnostics.seek(0)
                if b"assessment_finished=" in diagnostics.read():
                    marker = True
                    break
                time.sleep(0.02)
            require(marker, "Assessment did not reach closing collection")
            started = time.monotonic()
            if case != "missing":
                run_hook(args.provider, "closing", closing)
            stdout, _ = process.communicate(timeout=25)
            elapsed = time.monotonic() - started
            require(len(stdout) <= 65536, "Oversized CLI report")
            report = json.loads(stdout)
            safe = {key: report[key] for key in ("coverage", "hostVerification", "closingHostVerification",
                    "startup", "behaviour", "reasons") if key in report}
            safe.update(label=label, case=case, closingElapsedSeconds=round(elapsed, 3),
                        cliExit=process.returncode)
            result_path = workspace / (label + "-" + case + "-result.json")
            result_path.write_text(json.dumps(safe, indent=2) + "\n")
            require(before == identity(fixture.pid), "Fixture process identity changed")
            require(report.get("coverage") == expected, "Unexpected coverage conclusion")
            if case == "missing":
                require(process.returncode == 3 and elapsed >= 9.0,
                        "Missing evidence did not exercise the ten-second window")
                require("closing_host_attestation_missing" in report.get("reasons", []),
                        "Missing closing evidence reason absent")
            else:
                require(elapsed <= 20, "Closing collection/verification exceeded expected bound")
                require(report.get("hostVerification") == "VERIFIED"
                        and report.get("closingHostVerification") == "VERIFIED",
                        "Both native attestations must verify")
                require(not report.get("reasons"), "Assessment has unresolved evidence")
                require(report.get("behaviour") == ("BASELINE_OBSERVED" if label == "A" else "GUARD_OBSERVED"),
                        "Unexpected behavioural evidence")
                require(report.get("startup") == ("OPTION_ABSENT" if label == "A" else "EXPECTED_OPTION"),
                        "Unexpected startup evidence")
                require(process.returncode == (1 if label == "A" else 0), "Unexpected CLI exit code")
            # Explicit allowlist. Raw profiles, nonces, verifier output and JVM diagnostics stay private.
            safe.update(label=label, case=case, closingElapsedSeconds=round(elapsed, 3),
                        identityStable=True, cliExit=process.returncode)
            (workspace / (label + "-" + case + "-result.json")).write_text(json.dumps(safe, indent=2) + "\n")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
            diagnostics.seek(0)
            (directory / "cli.private.stderr").write_bytes(diagnostics.read(65536))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-kvm-host", action="store_true")
    parser.add_argument("--java")
    parser.add_argument("--cli")
    parser.add_argument("--fixture")
    parser.add_argument("--digest", help="Independently approved signed upatch payload digest")
    parser.add_argument("--provider", help="Trusted executable publishing real opening/closing profiles")
    parser.add_argument("--deploy", help="Administrator-approved executable loading the unchanged signed shield")
    args = parser.parse_args()
    require(platform.system() == "Linux", "Linux is required")
    if args.check_kvm_host:
        require(os.access("/dev/kvm", os.R_OK | os.W_OK), "KVM host access is unavailable")
        print("KVM device access check passed. Guest execution has not occurred.")
        return
    for key in ("java", "provider", "deploy"):
        require(getattr(args, key), "Missing --" + key)
        setattr(args, key, executable(getattr(args, key)))
    require(args.digest and re.fullmatch("[0-9a-f]{64}", args.digest), "Expected lowercase payload SHA-256")
    for key in ("cli", "fixture"):
        require(getattr(args, key), "Missing --" + key)
        path = Path(getattr(args, key))
        require(path.is_absolute() and path.is_file(), "JAR paths must be absolute readable files")
    for tool in ("bash", "systemd-detect-virt"):
        require(shutil.which(tool), "Missing prerequisite " + tool)
    vm = subprocess.run(["systemd-detect-virt", "--vm"], capture_output=True, text=True)
    require(vm.returncode == 0 and vm.stdout.strip() in ("kvm", "qemu"), "Expected KVM/QEMU Linux guest")
    require(Path("/dev/tpmrm0").exists() or Path("/dev/tpm0").exists(), "Guest TPM device is unavailable")
    runtime = Path("/run/shirudo/runtime/usr/bin/shirudo")
    require(runtime.is_file() and os.access(runtime, os.X_OK), "Authenticated Shirudo runtime is unavailable")
    require(not Path("/run/shirudo-java/java-sql-inj.jar").exists(),
            "Use a fresh guest with no previous java-sql-inj sealed JAR")
    version = subprocess.run([args.java, "-version"], capture_output=True, text=True, check=True)
    require(re.search(r'version "21[."]', version.stderr + version.stdout), "Java 21 is required")
    os.umask(0o077)
    workspace = Path(tempfile.mkdtemp(prefix="kintsugi-linux-validation-")).resolve()
    print("Private validation directory " + str(workspace), flush=True)
    env = dict(os.environ)
    for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        env.pop(name, None)
    fixtures = []
    try:
        for label in ("A", "B"):
            if label == "B":
                run_hook(args.deploy)
                require(Path("/run/shirudo-java/java-sql-inj.jar").is_file(), "Sealed shield JAR is absent")
            stderr = open(workspace / (label + ".private.stderr"), "wb")
            command = [args.java, "-XX:-EnableDynamicAgentLoading"]
            if label == "B":
                command += ["-javaagent:/run/shirudo-java/java-sql-inj.jar"]
            command += ["-jar", args.fixture, label]
            child = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=stderr, env=env)
            fixtures.append((child, stderr))
            require(select.select([child.stdout], [], [], 10)[0] and child.poll() is None,
                    "Fixture did not become ready before deployment or assessment")
            # A remains unchallenged and alive while the approved shield is deployed before B.
        for (child, _), label in zip(fixtures, ("A", "B")):
            assessment(args, child, label, "consistent", workspace,
                       "COVERAGE_GAP" if label == "A" else "BEHAVIOR_CORROBORATED")
        # New process supplies a new ready record for the negative collection-window case.
        child, stderr = fixtures[0]
        child.stdin.close()
        child.wait(timeout=5)
        child = subprocess.Popen([args.java, "-XX:-EnableDynamicAgentLoading", "-jar", args.fixture, "window"],
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=stderr, env=env)
        fixtures[0] = (child, stderr)
        assessment(args, child, "window", "missing", workspace, "UNKNOWN")
        manifest = {key: hashlib.sha256(Path(getattr(args, key)).read_bytes()).hexdigest() for key in ("cli", "fixture")}
        manifest["payloadDigest"] = args.digest
        (workspace / "provenance.json").write_text(json.dumps(manifest, indent=2) + "\n")
        print("Actual protected-guest checks passed. Review private logs before sharing sanitized result JSON.")
    finally:
        for child, log in fixtures:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait()
            log.close()

if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError, ValueError) as error:
        # Avoid leaking hook commands, profiles or raw child outputs.
        message = str(error) if isinstance(error, RuntimeError) else type(error).__name__
        print("Validation failed (" + message + "). Inspect private diagnostics and prerequisites.", file=sys.stderr)
        sys.exit(1)
