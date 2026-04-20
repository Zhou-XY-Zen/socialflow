"""
运维脚本共享工具：SSH 连接、命令执行、健康检查、冒烟测试。

敏感凭证优先从环境变量读取，未设置时回退到本地默认值；生产环境建议通过
`SOCIALFLOW_SSH_PASSWORD` / `SOCIALFLOW_MYSQL_PASSWORD` 等环境变量注入。
"""
import os
import sys
import time
from pathlib import Path

import paramiko


def utf8_stdout():
    """Windows 控制台默认 GBK，强制 UTF-8 输出以兼容 emoji / 中文"""
    if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
        try:
            sys.stdout.reconfigure(encoding="utf-8")
            sys.stderr.reconfigure(encoding="utf-8")
        except Exception:
            pass


HOST = os.environ.get("SOCIALFLOW_SSH_HOST", "***REDACTED-PROD-IP***")
USER = os.environ.get("SOCIALFLOW_SSH_USER", "root")
PASSWORD = os.environ.get("SOCIALFLOW_SSH_PASSWORD", "***REDACTED-SSH-PASSWORD***")

REMOTE_DIR = os.environ.get("SOCIALFLOW_REMOTE_DIR", "/opt/socialflow")
REMOTE_WEB = f"{REMOTE_DIR}/web"

MYSQL_USER = os.environ.get("SOCIALFLOW_MYSQL_USER", "root")
MYSQL_PASS = os.environ.get("SOCIALFLOW_MYSQL_PASSWORD", "***REDACTED-DB-PASSWORD***")
MYSQL_DB = os.environ.get("SOCIALFLOW_MYSQL_DB", "socialflow")

HEALTH_URL = "http://127.0.0.1:8080/actuator/health"
DEFAULT_SMOKE_TESTS = [
    ("Nginx 首页", "http://127.0.0.1/"),
    ("/auth/me (期望 401)", "http://127.0.0.1/api/v1/auth/me"),
    ("/actuator/prometheus", "http://127.0.0.1:8080/actuator/prometheus"),
    ("/actuator/info", "http://127.0.0.1:8080/actuator/info"),
]


def connect_ssh(timeout=15):
    """建立到生产服务器的 SSH 连接，返回 (ssh, sftp)。调用方负责在 finally 中关闭。"""
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASSWORD, timeout=timeout)
    sftp = ssh.open_sftp()
    return ssh, sftp


def run(ssh, cmd, check=False, silent=False, timeout=60):
    """执行远程命令，返回 (stdout, stderr, exit_code)。check=True 时失败抛异常。"""
    if not silent:
        print(f"$ {cmd}")
    _, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if out.strip() and not silent:
        print(out)
    if err.strip() and not silent:
        print(f"[stderr] {err}", file=sys.stderr)
    if check and exit_code != 0:
        raise RuntimeError(f"Remote cmd failed (exit {exit_code}): {cmd}")
    return out, err, exit_code


def wait_health(ssh, max_wait=180, interval=3):
    """轮询 /actuator/health 直到 200 或超时。返回 True 表示启动成功。"""
    print(f"\n== 等待后端启动（最多 {max_wait}s）==")
    started = time.time()
    while time.time() - started < max_wait:
        out, _, code = run(
            ssh,
            f"curl -s -o /tmp/health.json -w '%{{http_code}}' {HEALTH_URL}",
            silent=True,
        )
        if code == 0 and out.strip() == "200":
            body, _, _ = run(ssh, "cat /tmp/health.json", silent=True)
            print(f"  ✓ actuator/health 返回 200: {body.strip()[:300]}")
            return True
        elapsed = int(time.time() - started)
        print(f"  [{elapsed:3d}s] HTTP={out.strip() or '---'}  ", end="\r", flush=True)
        time.sleep(interval)
    print()
    return False


def smoke_test(ssh, endpoints=None):
    """批量请求 URL，打印每个的 HTTP 状态码。"""
    endpoints = endpoints or DEFAULT_SMOKE_TESTS
    print("\n== Smoke Test ==")
    for label, url in endpoints:
        out, _, _ = run(
            ssh,
            f"curl -s -o /dev/null -w 'HTTP=%{{http_code}}' '{url}'",
            silent=True,
        )
        print(f"  {label:40s} → {out.strip()}")


def tail_flyway_log(ssh, lines=20):
    """抓取启动日志中 flyway 相关行。"""
    print("\n== Flyway 执行结果 ==")
    out, _, _ = run(
        ssh,
        f"grep -iE 'flyway|migrating|schema_history|baseline' {REMOTE_DIR}/logs/app.log | tail -{lines} || true",
        silent=True,
    )
    print(out or "  (无 Flyway 日志输出)")


PID_FILE = f"{REMOTE_DIR}/.last-start.pid"


def start_backend(ssh, profile="prod"):
    """启动后端并返回新进程 PID。

    paramiko 在 nohup java 场景下会 channel hang —— 即便用了 `< /dev/null`、
    `disown`、`echo $!; exit`，只要 Python 侧尝试 read/readline，都会卡到 channel
    最终关闭（而 java 进程继承了 channel 的 fd，channel 根本不会主动关）。

    彻底方案：
      1. 用 `transport.open_session()` 开独立 channel 发启动命令
      2. 远端把 PID 写到文件 `{REMOTE_DIR}/.last-start.pid`
      3. 发完命令 sleep 1.5s 让 shell 把 PID 写入文件
      4. 直接 close 这个 channel —— 不 read 就不会 hang
      5. 回到主 channel，用 `cat` 读 PID 文件；读不到再 pgrep 兜底

    这样主 ssh 会话完全不受影响，后续的 wait_health / smoke_test 都能正常走。
    """
    # 黄山版 4.10：源码不放明文密码 → 启动时通过环境变量注入。
    # /opt/socialflow/.env 是服务器本地维护的密钥文件，权限 600，不进 git。
    # set -a 让后续变量定义自动 export；source 后 set +a 关闭。
    env_load = f"set -a; [ -f {REMOTE_DIR}/.env ] && . {REMOTE_DIR}/.env; set +a; "
    start_cmd = (
        f"cd {REMOTE_DIR} && "
        f"{env_load}"
        f"SPRING_PROFILES_ACTIVE={profile} "
        f"nohup java -Xms512m -Xmx1536m "
        f"-jar socialflow.jar > {REMOTE_DIR}/logs/app.log 2>&1 < /dev/null & "
        f"echo $! > {PID_FILE}; disown"
    )
    print(f"$ {start_cmd}")

    # 开独立 session channel 发命令，不读 stdout，立即关闭
    transport = ssh.get_transport()
    channel = transport.open_session()
    try:
        channel.exec_command(start_cmd)
        # 给 shell 时间执行到 `echo $! > pid_file` 这一步
        time.sleep(1.5)
    finally:
        try:
            channel.close()
        except Exception:
            pass

    # 在主 channel 上读 PID（此时 channel 已开新的 session，互不干扰）
    out, _, _ = run(ssh, f"cat {PID_FILE} 2>/dev/null", silent=True)
    pid = out.strip()
    if not pid.isdigit():
        time.sleep(2)
        out2, _, _ = run(
            ssh,
            "pgrep -f 'java.*socialflow.jar' | grep -v nacos | tail -1",
            silent=True,
        )
        pid = out2.strip()
    return pid or "unknown"


def kill_backend(ssh, force_grace=5):
    """停掉现有后端，返回被杀的 PID 列表。"""
    pid_out, _, _ = run(
        ssh,
        "pgrep -f 'java.*socialflow.jar' | grep -v nacos || true",
        silent=True,
    )
    pids = [p for p in pid_out.strip().split("\n") if p]
    if not pids:
        print("  (无运行中的 socialflow 进程)")
        return []
    for pid in pids:
        print(f"  kill {pid}")
        run(ssh, f"kill {pid}", check=False)
    time.sleep(force_grace)
    still, _, _ = run(ssh, "pgrep -f 'java.*socialflow.jar' | grep -v nacos || true", silent=True)
    if still.strip():
        print("  ⚠ 进程仍在，强制 kill -9")
        run(ssh, "pkill -9 -f 'java.*socialflow.jar' || true", check=False)
        time.sleep(3)
    return pids


def change_to_project_root():
    """把 CWD 切到脚本所在目录（项目根），保证相对路径稳定。"""
    os.chdir(Path(__file__).parent)
