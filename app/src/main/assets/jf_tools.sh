#!/system/bin/sh
# ============================================================
# 极风工具箱 预装工具集 (部署到 /sdcard/JFToolbox/bin)
# 终端首次启动自动安装, 无需任何额外操作。
# 基于 Android 自带 toybox/toolbox, 纯 POSIX sh, 全机型可用。
# ============================================================

JF_BIN="${JF_TOOLS_DIR:-/sdcard/JFToolbox/bin}"
mkdir -p "$JF_BIN" 2>/dev/null

install_cmd() {
    name="$1"; body="$2"
    printf '%s\n' "$body" > "$JF_BIN/$name" 2>/dev/null
    chmod 755 "$JF_BIN/$name" 2>/dev/null
}

# ll: 详细列表 (等同 ls -alh)
install_cmd ll 'ls -alh "$@"'

# l: 单列列表
install_cmd l 'ls -1 "$@"'

# llrt: 按时间倒序列表
install_cmd llrt 'ls -alt "$@"'

# touch 兼容 (老系统 toolbox 可能无 touch)
if ! command -v touch >/dev/null 2>&1; then
install_cmd touch 'for f in "$@"; do [ -e "$f" ] || : > "$f"; done'
fi

# tree: 简易目录树 (toybox 无 tree)
install_cmd tree '
d="${1:-.}"; lvl="${2:-3}"
find "$d" -maxdepth "$lvl" 2>/dev/null | sort | while read -r p; do
  rel="${p#$d}"
  depth=$(printf "%s" "$rel" | tr -cd "/" | wc -c)
  i=0; pad=""
  while [ "$i" -lt "$depth" ]; do pad="  $pad"; i=$((i+1)); done
  name="${p##*/}"
  if [ -d "$p" ]; then echo "$pad[$name]/"; else echo "$pad$name"; fi
done'

# duh: 当前目录各文件夹大小
install_cmd duh 'du -sh * 2>/dev/null | sort -hr'

# freespace: 存储空间概览
install_cmd freespace 'df -h 2>/dev/null | grep -v tmpfs'

# myip: 本机 IP
install_cmd myip 'ip -4 addr show 2>/dev/null | grep inet | grep -v 127.0.0.1 | awk "{print \$2, \$NF}"'

# psgrep: 进程查找 (psgrep 关键字)
install_cmd psgrep 'ps -ef 2>/dev/null | grep -i "$1" | grep -v grep'

# jf-send / jf-recv 提示: 文件传输走终端内置 push/pull
install_cmd jf-send 'echo "用法: push <本地文件> [目标路径]  (在终端直接输入, 无需加命令前缀)"'
install_cmd jf-recv 'echo "用法: pull <目标文件> [本地路径]  (在终端直接输入, 无需加命令前缀)"'

# now: 当前时间
install_cmd now 'date "+%Y-%m-%d %H:%M:%S"'

echo "JF_TOOLS_READY:$JF_BIN"
