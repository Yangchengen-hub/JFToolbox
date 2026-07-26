#!/system/bin/sh
# ============================================================
# 极风工具箱 Shell 权限中枢 daemon
# 以 shell 权限 (uid 2000) 常驻运行, 监听 Unix socket
# 供控制端 APP (经 adb shell oneshot) 与设备端其他 APP (经 socket) 调用
#
# 用法:
#   jf_daemon.sh daemon                # 启动常驻 daemon (后台)
#   jf_daemon.sh oneshot <base64-req>  # 处理单个 JSON 请求 (一次性)
#   jf_daemon.sh status               # 输出 daemon 状态 (JSON, 单行)
#   jf_daemon.sh stop                  # 停止 daemon
#   jf_daemon.sh _handle <req>        # 内部: 处理一个请求并输出响应
#
# 请求协议 (JSON 单行):
#   {"cmd":"exec","command":"<sh>","timeout":10000}
#   {"cmd":"status"}
#   {"cmd":"stop"}
# 响应协议 (JSON 单行):
#   {"exit":0,"stdout":"...","stderr":"..."}
#   {"pid":123,"uptime":45,"alive":1}
# ============================================================

SOCK=/data/local/tmp/jf_daemon.sock
PIDFILE=/data/local/tmp/jf_daemon.pid
STARTFILE=/data/local/tmp/jf_daemon.start
LOGFILE=/data/local/tmp/jf_daemon.log
ERRFILE=/data/local/tmp/jf_daemon.err
REQ_FIFO=/data/local/tmp/jf_daemon.req.fifo
RESP_FIFO=/data/local/tmp/jf_daemon.resp.fifo

# 日志写入 (失败不致命)
log() { echo "[$(date '+%H:%M:%S')] $*" >> "$LOGFILE" 2>/dev/null; }

# -------- JSON 工具 (不依赖 jq) --------

# 从 JSON 字符串提取字段值 (字符串或数字)
# 用法: json_get '<json>' '<key>'
json_get() {
    _jg_json="$1"
    _jg_key="$2"
    # 优先匹配 "key":"value"
    _jg_val=$(printf '%s' "$_jg_json" | sed -n "s/.*\"$_jg_key\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p")
    if [ -n "$_jg_val" ]; then printf '%s' "$_jg_val"; return; fi
    # 再匹配数字 "key":123
    _jg_val=$(printf '%s' "$_jg_json" | sed -n "s/.*\"$_jg_key\"[[:space:]]*:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p")
    printf '%s' "$_jg_val"
}

# 把字符串转义为 JSON 字符串内容 (处理 \ " 和换行, 输出单行)
json_escape() {
    # 1. 反斜杠 → 双反斜杠
    # 2. 双引号 → \"
    # 3. 用 awk 把换行替换为字面 \n (ORS 技巧)
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk 'BEGIN{ORS="\\n"} {print}'
}

# -------- 命令执行 --------

# 执行单条 shell 命令, 输出 JSON 结果 (单行)
do_exec() {
    _de_cmd="$1"
    _de_toms="${2:-10000}"
    _de_secs=$((_de_toms / 1000))
    [ "$_de_secs" -lt 1 ] && _de_secs=1
    _de_out=""
    _de_err=""
    _de_code=0
    if command -v timeout >/dev/null 2>&1; then
        # toybox timeout: SIGTERM 后退出码 124
        _de_out=$(timeout "$_de_secs" sh -c "$_de_cmd" 2>"$ERRFILE")
        _de_code=$?
    else
        _de_out=$(eval "$_de_cmd" 2>"$ERRFILE")
        _de_code=$?
    fi
    _de_err=$(cat "$ERRFILE" 2>/dev/null)
    rm -f "$ERRFILE" 2>/dev/null
    _de_eo=$(json_escape "$_de_out")
    _de_ee=$(json_escape "$_de_err")
    printf '{"exit":%d,"stdout":"%s","stderr":"%s"}\n' "$_de_code" "$_de_eo" "$_de_ee"
}

# 处理单个 JSON 请求, 输出 JSON 响应 (单行)
handle_request() {
    _hr_req="$1"
    _hr_cmd=$(json_get "$_hr_req" "cmd")
    case "$_hr_cmd" in
        exec)
            _hr_command=$(json_get "$_hr_req" "command")
            _hr_timeout=$(json_get "$_hr_req" "timeout")
            do_exec "$_hr_command" "$_hr_timeout"
            ;;
        status)
            _hr_pid=$(cat "$PIDFILE" 2>/dev/null)
            _hr_start=$(cat "$STARTFILE" 2>/dev/null)
            _hr_now=$(date +%s)
            _hr_uptime=0
            if [ -n "$_hr_start" ] 2>/dev/null; then
                _hr_uptime=$((_hr_now - _hr_start))
            fi
            _hr_alive=0
            if [ -n "$_hr_pid" ] && kill -0 "$_hr_pid" 2>/dev/null; then
                _hr_alive=1
            fi
            printf '{"pid":%s,"uptime":%s,"alive":%d}\n' "${_hr_pid:-0}" "${_hr_uptime}" "$_hr_alive"
            ;;
        stop)
            _hr_pid=$(cat "$PIDFILE" 2>/dev/null)
            if [ -n "$_hr_pid" ] && kill -0 "$_hr_pid" 2>/dev/null; then
                kill "$_hr_pid" 2>/dev/null
                rm -f "$PIDFILE" "$SOCK" "$STARTFILE" "$REQ_FIFO" "$RESP_FIFO"
                printf '{"cmd":"stop","ok":1}\n'
            else
                rm -f "$PIDFILE" "$SOCK" "$STARTFILE"
                printf '{"cmd":"stop","ok":0,"msg":"not running"}\n'
            fi
            ;;
        *)
            printf '{"exit":-1,"stdout":"","stderr":"unknown cmd: %s"}\n' "$_hr_cmd"
            ;;
    esac
}

# -------- 模式分发 --------

# oneshot: $2 = base64 编码的 JSON 请求
if [ "$1" = "oneshot" ]; then
    _os_req=$(printf '%s' "$2" | base64 -d 2>/dev/null)
    if [ -z "$_os_req" ]; then
        printf '{"exit":-1,"stdout":"","stderr":"empty request"}\n'
        exit 1
    fi
    handle_request "$_os_req"
    exit 0
fi

# status: 输出 daemon 当前状态
if [ "$1" = "status" ]; then
    _st_pid=$(cat "$PIDFILE" 2>/dev/null)
    _st_start=$(cat "$STARTFILE" 2>/dev/null)
    _st_now=$(date +%s)
    _st_uptime=0
    if [ -n "$_st_start" ] 2>/dev/null; then
        _st_uptime=$((_st_now - _st_start))
    fi
    _st_alive=0
    if [ -n "$_st_pid" ] && kill -0 "$_st_pid" 2>/dev/null; then
        _st_alive=1
    fi
    printf '{"pid":%s,"uptime":%s,"alive":%d}\n' "${_st_pid:-0}" "${_st_uptime}" "$_st_alive"
    exit 0
fi

# stop: 停止 daemon
if [ "$1" = "stop" ]; then
    _sp_pid=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$_sp_pid" ] && kill -0 "$_sp_pid" 2>/dev/null; then
        kill "$_sp_pid" 2>/dev/null
        rm -f "$PIDFILE" "$SOCK" "$STARTFILE" "$REQ_FIFO" "$RESP_FIFO"
        echo "stopped pid=$_sp_pid"
    else
        rm -f "$PIDFILE" "$SOCK" "$STARTFILE"
        echo "not running"
    fi
    exit 0
fi

# _handle: 内部使用, 处理单个请求 (供 nc 协作模式调用)
if [ "$1" = "_handle" ]; then
    handle_request "$2"
    exit 0
fi

# ============== daemon 模式 (默认) ==============

# 已运行检测
if [ -f "$PIDFILE" ]; then
    _oldpid=$(cat "$PIDFILE")
    if kill -0 "$_oldpid" 2>/dev/null; then
        echo "already running pid=$_oldpid"
        exit 0
    fi
fi

# 写 pid 和启动时间
echo $$ > "$PIDFILE"
date +%s > "$STARTFILE"
log "daemon 启动 pid=$$"

# 清理旧 socket / fifo
rm -f "$SOCK" "$REQ_FIFO" "$RESP_FIFO" 2>/dev/null

# 退出时清理
cleanup() {
    rm -f "$SOCK" "$PIDFILE" "$STARTFILE" "$REQ_FIFO" "$RESP_FIFO" 2>/dev/null
    log "daemon 退出 pid=$$"
    exit 0
}
trap cleanup INT TERM

# 检查 nc 是否支持 -U (Unix socket)
_HAVE_NC_U=0
if command -v nc >/dev/null 2>&1; then
    if nc -h 2>&1 | grep -q -- '-U'; then
        _HAVE_NC_U=1
    fi
fi

if [ "$_HAVE_NC_U" = "1" ]; then
    # ----- nc -U 模式: 真 Unix socket 服务 -----
    log "使用 nc -U 监听 $SOCK"
    # mkfifo 用于在 nc 与本脚本之间传递数据
    if ! mkfifo "$REQ_FIFO" "$RESP_FIFO" 2>/dev/null; then
        log "mkfifo 失败, 退化为 sleep 模式"
        _HAVE_NC_U=0
    fi
fi

if [ "$_HAVE_NC_U" = "1" ]; then
    while true; do
        # 用 fd 3 保持 RESP_FIFO 写端打开, 避免 nc 读到 EOF 立即退出
        exec 3>"$RESP_FIFO"
        # nc: stdin ← RESP_FIFO, stdout → REQ_FIFO
        #     同时 accept 一个 Unix socket 连接: socket → stdout, stdin → socket
        nc -U -l "$SOCK" <"$RESP_FIFO" >"$REQ_FIFO" &
        _ncpid=$!
        # 阻塞读一行请求
        if IFS= read -r _line <"$REQ_FIFO" 2>/dev/null; then
            if [ -n "$_line" ]; then
                _resp=$(handle_request "$_line")
                printf '%s\n' "$_resp" >&3
            fi
        fi
        wait $_ncpid 2>/dev/null
        exec 3>&-
        # 短暂 sleep 防止空转
        sleep 0.2
    done
else
    # ----- 回退模式: 仅 oneshot 可用 -----
    # daemon 维持 pid 文件, 让控制端能通过 status 检测存活
    # ShellHub.exec 通过 adb shell 调用本脚本 oneshot 模式工作
    log "nc -U 不可用, 进入保活模式 (仅 oneshot 可用)"
    while true; do
        sleep 3600
    done
fi
