import concurrent.futures, socket, subprocess, sys

def ping(ip):
    try:
        r = subprocess.run(["ping", "-n", "1", "-w", "250", ip],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return r.returncode == 0
    except Exception:
        return False

def adb_open(ip, port=5555, timeout=0.4):
    try:
        s = socket.create_connection((ip, port), timeout=timeout)
        s.close()
        return True
    except Exception:
        return False

ips = ["192.168.1.%d" % i for i in range(1, 255)]
alive = []
with concurrent.futures.ThreadPoolExecutor(max_workers=80) as ex:
    futs = {ex.submit(ping, ip): ip for ip in ips}
    for f in concurrent.futures.as_completed(futs):
        ip = futs[f]
        if f.result():
            alive.append(ip)

alive.sort(key=lambda x: list(map(int, x.split('.'))))
print("ALIVE (%d):" % len(alive))
for ip in alive:
    adb = adb_open(ip)
    # also try 5556
    adb2 = adb_open(ip, 5556) if not adb else False
    mark = "ADB:5555" if adb else ("ADB:5556" if adb2 else "no-adb")
    print("  %s  %s" % (ip, mark))

# hostname/mac hints via arp
print("\nARP (192.168.1.*):")
try:
    out = subprocess.check_output(["arp", "-a"], universal_newlines=True, errors="replace")
    for line in out.splitlines():
        if "192.168.1." in line and "dynamic" in line.lower():
            print(" ", line.strip())
except Exception as e:
    print(e)
