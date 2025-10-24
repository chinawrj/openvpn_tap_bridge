#!/system/bin/sh
# vpn-hotspot-bridge.sh — Pixel 8a (root)
# Bridge OpenVPN TAP <-> Wi-Fi AP + ncm0 using brctl (no ip link master).
# - Adds AP (wlan1/ap0/softap0) & ncm0 to br0 when UP
# - Removes them from br0 when DOWN or missing
# - Starts OpenVPN (daemon); expects client .ovpn already set (e.g., port 11194)
# Logs: /data/local/tmp/vpn-bridge.log

LOG=/data/local/tmp/vpn-bridge.log
OPENVPN_PID=/data/local/tmp/openvpn.pid
CFG_PRIMARY={{OVPN_CONFIG_PATH}}
CFG_FALLBACK=/sdcard/pixel8a.ovpn
BR=br0
VPN_IF=tap0

AP_FALLBACKS="{{AP_INTERFACES}}"
NCM_IF="{{NCM_INTERFACE}}"

log(){ echo "[$(date '+%F %T')] $*" >> "$LOG"; }
exists_if(){ [ -d "/sys/class/net/$1" ]; }
is_up(){ [ "$(cat /sys/class/net/$1/operstate 2>/dev/null)" = "up" ]; }

# Return 0 if IF is enslaved to $BR
enslaved_to_br(){
  IF="$1"
  [ -z "$IF" ] && return 1
  if [ -L "/sys/class/net/$IF/brport/bridge" ]; then
    CUR="$(basename "$(readlink -f "/sys/class/net/$IF/brport/bridge")" 2>/dev/null)"
    [ "$CUR" = "$BR" ] && return 0
  fi
  return 1
}

# Detect current SoftAP iface by iw (type AP), else fallbacks
find_ap_if(){
  if command -v iw >/dev/null 2>&1; then
    AP="$(iw dev 2>/dev/null | awk '$1=="Interface"{i=$2} $1=="type"&&$2=="AP"{print i}' | head -n1)"
    [ -n "$AP" ] && { echo "$AP"; return 0; }
  fi
  for C in $AP_FALLBACKS; do
    exists_if "$C" && { echo "$C"; return 0; }
  done
  return 1
}

# Bridge ops (brctl-only)
ensure_bridge(){
  if ! exists_if "$BR"; then
    log "creating $BR with brctl"
    brctl addbr "$BR" 2>>"$LOG"
    brctl stp "$BR" off 2>>"$LOG"
  fi
  if command -v ifconfig >/dev/null 2>&1; then
    ifconfig "$BR" up 2>/dev/null
  fi
}

add_to_bridge_brctl(){
  IF="$1"
  [ -z "$IF" ] && { log "add_to_bridge: empty IF"; return 1; }
  if enslaved_to_br "$IF"; then
    return 0
  fi
  if brctl addif "$BR" "$IF" 2>>"$LOG"; then
    log "enslaved $IF -> $BR via brctl"
  else
    RC=$?
    log "brctl addif failed for $IF (rc=$RC)"
  fi
}

remove_from_bridge_brctl(){
  IF="$1"
  [ -z "$IF" ] && return 0
  if enslaved_to_br "$IF"; then
    if brctl delif "$BR" "$IF" 2>>"$LOG"; then
      log "removed $IF from $BR via brctl (down/missing)"
    else
      RC=$?
      log "brctl delif failed for $IF (rc=$RC)"
    fi
  fi
}

dump_status(){
  {
    echo "===== brctl show ====="; brctl show
    echo "===== ifconfig $BR ====="; ifconfig "$BR" 2>/dev/null || true
    APIF_NOW="$(find_ap_if)"; [ -n "$APIF_NOW" ] && { echo "===== $APIF_NOW operstate ====="; cat /sys/class/net/$APIF_NOW/operstate 2>/dev/null; }
    exists_if "$NCM_IF" && { echo "===== $NCM_IF operstate ====="; cat /sys/class/net/$NCM_IF/operstate 2>/dev/null; }
    exists_if "$VPN_IF" && { echo "===== ifconfig $VPN_IF ====="; ifconfig "$VPN_IF"; }
    echo "======================"
  } >>"$LOG" 2>&1
}

# ---------------- boot sequence ----------------
sleep 25
log "service start"

# Start OpenVPN once (daemon)
CFG=""
[ -f "$CFG_PRIMARY" ] && CFG="$CFG_PRIMARY"
[ -z "$CFG" ] && [ -f "$CFG_FALLBACK" ] && CFG="$CFG_FALLBACK"
if [ -n "$CFG" ]; then
  if ! pgrep -f "openvpn.*--config .*pixel8a\.ovpn" >/dev/null 2>&1; then
    log "starting openvpn with $CFG"
    openvpn --config "$CFG" --daemon --log /data/local/tmp/openvpn.log --writepid "$OPENVPN_PID"
  else
    log "openvpn already running"
  fi
else
  log "ERROR: no .ovpn found at $CFG_PRIMARY or $CFG_FALLBACK"
fi

ensure_bridge

LAST_AP_IFIDX=""
LAST_TAP_IFIDX=""
LAST_NCM_IFIDX=""

log "bridge watcher loop start"
while true; do
  ensure_bridge

  # --- AP iface (observe-only) ---
  APIF="$(find_ap_if)"
  if [ -n "$APIF" ] && exists_if "$APIF"; then
    IFIDX="$(cat /sys/class/net/$APIF/ifindex 2>/dev/null)"
    OPSTATE="$(cat /sys/class/net/$APIF/operstate 2>/dev/null)"
    if [ "$IFIDX" != "$LAST_AP_IFIDX" ]; then
      log "AP=$APIF ifindex now $IFIDX (was $LAST_AP_IFIDX), operstate=$OPSTATE"
      LAST_AP_IFIDX="$IFIDX"
    fi
    if [ "$OPSTATE" = "up" ]; then
      add_to_bridge_brctl "$APIF"
    else
      remove_from_bridge_brctl "$APIF"
      log "AP=$APIF not UP (operstate=$OPSTATE) -> ensured not in $BR"
    fi
  else
    # missing entirely: ensure removed if previously enslaved
    for C in $AP_FALLBACKS; do
      exists_if "$C" || remove_from_bridge_brctl "$C"
    done
    log "AP iface not present (yet)"
  fi

  # --- ncm0 (observe-only) ---
  if exists_if "$NCM_IF"; then
    NIFIDX="$(cat /sys/class/net/$NCM_IF/ifindex 2>/dev/null)"
    NOPSTATE="$(cat /sys/class/net/$NCM_IF/operstate 2>/dev/null)"
    if [ "$NIFIDX" != "$LAST_NCM_IFIDX" ]; then
      log "$NCM_IF ifindex now $NIFIDX (was $LAST_NCM_IFIDX), operstate=$NOPSTATE"
      LAST_NCM_IFIDX="$NIFIDX"
    fi
    if [ "$NOPSTATE" = "up" ]; then
      add_to_bridge_brctl "$NCM_IF"
    else
      remove_from_bridge_brctl "$NCM_IF"
      log "$NCM_IF not UP (operstate=$NOPSTATE) -> ensured not in $BR"
    fi
  else
    remove_from_bridge_brctl "$NCM_IF"
    log "$NCM_IF not present (yet)"
  fi

  # --- TAP iface (safe to manage) ---
  if exists_if "$VPN_IF"; then
    IFIDX="$(cat /sys/class/net/$VPN_IF/ifindex 2>/dev/null)"
    if [ "$IFIDX" != "$LAST_TAP_IFIDX" ]; then
      log "$VPN_IF ifindex now $IFIDX (was $LAST_TAP_IFIDX)"
      LAST_TAP_IFIDX="$IFIDX"
    fi
    add_to_bridge_brctl "$VPN_IF"
    if command -v ifconfig >/dev/null 2>&1; then
      ifconfig "$VPN_IF" up 2>/dev/null
    fi
  else
    log "$VPN_IF not present (yet)"
  fi

  # periodic status (every ~60s)
  TS=$(date +%s)
  if [ $((TS % 60)) -lt 2 ]; then
    log "status tick"; dump_status
  fi
  sleep 5
done &
log "service setup complete (watcher bg)"
exit 0
