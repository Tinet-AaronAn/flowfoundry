#!/usr/bin/env python3
"""Start a process detached from the invoking shell (survives Cursor agent sessions)."""
from __future__ import annotations

import os
import subprocess
import sys


def main() -> None:
    if len(sys.argv) < 5 or sys.argv[3] != "--":
        print(
            "usage: java-daemon.py <pidfile> <logfile> -- <command...>",
            file=sys.stderr,
        )
        sys.exit(2)

    pidfile = sys.argv[1]
    logfile = sys.argv[2]
    cmd = sys.argv[4:]
    if not cmd:
        print("empty command", file=sys.stderr)
        sys.exit(2)

    if os.fork() > 0:
        os._exit(0)
    os.setsid()
    if os.fork() > 0:
        os._exit(0)

    os.chdir("/")
    os.umask(0o22)
    with open(logfile, "a", buffering=1, encoding="utf-8") as log:
        proc = subprocess.Popen(
            cmd,
            stdout=log,
            stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL,
            start_new_session=True,
        )
    with open(pidfile, "w", encoding="utf-8") as handle:
        handle.write(str(proc.pid))
    os._exit(0)


if __name__ == "__main__":
    main()
