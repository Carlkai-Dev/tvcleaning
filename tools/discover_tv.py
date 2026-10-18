#!/usr/bin/env python3
"""Local network discovery script for Android TV on port 5555.
Checks ARP cache and optionally scans local subnet.
"""
import os
import re
import socket
import subprocess
import sys


def get_arp_ips():
    ips = set()
    try:
        out = subprocess.check_output(["ip", "neigh"], text=True)
        for line in out.splitlines():
            parts = line.split()
            if len(parts) >= 1 and re.match(r"^\d+\.\d+\.\d+\.\d+$", parts[0]):
                ips.add(parts[0])
    except Exception:
        pass
    try:
        out = subprocess.check_output(["arp", "-a"], text=True)
        for line in out.splitlines():
            m = re.search(r"\((\d+\.\d+\.\d+\.\d+)\)", line)
            if m:
                ips.add(m.group(1))
    except Exception:
        pass
    return ips


def check_port_5555(ip, timeout=1.0):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(timeout)
            if s.connect_ex((ip, 5555)) == 0:
                return True
    except Exception:
        pass
    return False


def main():
    tv_host = os.environ.get("TV_HOST")
    if tv_host:
        if check_port_5555(tv_host):
            print(f"[+] TV found at configured TV_HOST={tv_host}:5555")
            sys.exit(0)
        else:
            print(f"[-] Configured TV_HOST={tv_host}:5555 did not respond.")

    print("[*] Probing local ARP entries for ADB on port 5555...")
    candidates = []
    for ip in get_arp_ips():
        if check_port_5555(ip):
            candidates.append(ip)
            print(f"[+] Found active ADB endpoint: {ip}:5555")

    if len(candidates) == 1:
        print(f"[+] Selected TV IP: {candidates[0]}")
        sys.exit(0)
    elif len(candidates) > 1:
        print(f"[!] Multiple active ADB devices found: {', '.join(candidates)}")
        sys.exit(0)
    else:
        print("[-] No active ADB endpoints found via ARP table.")
        sys.exit(1)


if __name__ == "__main__":
    main()
