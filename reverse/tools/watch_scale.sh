#!/bin/zsh
# 监听设备蓝牙抓包，一旦出现体脂秤 (80:EA:CA:00:70:DF) 的流量就拉取并解析。
# 用法: watch_scale.sh [最大轮数]
set -u
ROOT="/Users/jhonhe/Public/Projects/hackqing/reverse"
OUT="$ROOT/capture/watch"
mkdir -p "$OUT"
# little-endian 广播地址
NEEDLE=$(python3 -c "print(bytes([0xdf,0x70,0x00,0xca,0xea,0x80]).hex())")
MAX=${1:-120}
i=0
while [ $i -lt $MAX ]; do
  i=$((i+1))
  NEW=$(adb shell "su -c 'ls -t /data/misc/bluetooth/cached_hci/*.cfa 2>/dev/null | head -2'" | tr -d '\r')
  [ -z "$NEW" ] && { sleep 6; continue; }
  for f in ${(f)NEW}; do
    b=$(basename "$f")
    adb shell "su -c 'cp $f /data/local/tmp/hc/$b; chmod 666 /data/local/tmp/hc/$b'" >/dev/null 2>&1
    adb pull "/data/local/tmp/hc/$b" "$OUT/$b" >/dev/null 2>&1
  done
  HIT=$(python3 - "$OUT" "$NEEDLE" <<'PY'
import sys, glob, os
d, needle = sys.argv[1], bytes.fromhex(sys.argv[2])
for f in sorted(glob.glob(os.path.join(d, "*.cfa")), key=os.path.getmtime, reverse=True)[:2]:
    try:
        if needle in open(f, "rb").read():
            print(f); break
    except Exception:
        pass
PY
)
  if [ -n "$HIT" ]; then
    echo "FOUND_SCALE_TRAFFIC: $HIT"
    python3 "$ROOT/tools/btsnoop_att.py" "$HIT" > "$OUT/att_live.txt" 2>&1
    echo "PARSED -> $OUT/att_live.txt"
    exit 0
  fi
  sleep 6
done
echo "TIMEOUT: no scale traffic seen in $MAX rounds"
