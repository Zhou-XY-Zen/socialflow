"""
运维脚本共享工具：SSH 连接、命令执行、健康检查、冒烟测试。

凭证一律通过环境变量注入，未设置时直接终止脚本，避免把密码写进源码或 git 历史。
推荐做法：在本机维护一个不进 git 的 ~/.socialflow.env，运行前 `source` 一下即可。

必需的环境变量：
  SOCIALFLOW_SSH_HOST       生产机 IP / 域名
  SOCIALFLOW_SSH_PASSWORD   SSH 密码（建议改用密钥登录）
  SOCIALFLOW_MYSQL_PASSWORD MySQL 密码

可选环境变量（带合理默认值）：
  SOCIALFLOW_SSH_USER       默认 root
  SOCIALFLOW_REMOTE_DIR     默认 /opt/socialflow
  SOCIALFLOW_MYSQL_USER     默认 root
  SOCIALFLOW_MYSQL_DB       默认 socialflow
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


def _require_env(name: str) -> str:
    """读取必需的环境变量。未设置时打印中文错误并以非 0 退出，避免脚本继续往下跑。"""
    value = os.environ.get(name)
    if not value:
        print(
            f"❌ 缺少环境变量 {name}\n"
            f"   该脚本需要 SSH/MySQL 凭证才能运行。请先 export 相关变量，例如：\n"
            f"     export SOCIALFLOW_SSH_HOST=<生产机 IP>\n"
            f"     export SOCIALFLOW_SSH_PASSWORD='<SSH 密码>'\n"
            f"     export SOCIALFLOW_MYSQL_PASSWORD='<MySQL 密码>'\n"
            f"   建议把这些写到不进 git 的本地文件里（例如 ~/.socialflow.env），运行前 source 一下。",
            file=sys.stderr,
        )
        sys.exit(78)  # EX_CONFIG
    return value


HOST = _require_env("SOCIALFLOW_SSH_HOST")
USER = os.environ.get("SOCIALFLOW_SSH_USER", "root")
PASSWORD = _require_env("SOCIALFLOW_SSH_PASSWORD")

REMOTE_DIR = os.environ.get("SOCIALFLOW_REMOTE_DIR", "/opt/socialflow")
REMOTE_WEB = f"{REMOTE_DIR}/web"

MYSQL_USER = os.environ.get("SOCIALFLOW_MYSQL_USER", "root")
MYSQL_PASS = _require_env("SOCIALFLOW_MYSQL_PASSWORD")
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


# ---------------------------------------------------------------------------
# Pre-flight check
# ---------------------------------------------------------------------------
# 思路：部署前先把"会让部署失败的环境问题"全检完，再动停机。
# 之前的脚本是先 kill_backend 再校验，结果有时是远端磁盘满 / Java 版本不对，
# 部署中断后用户看到的是宕机+失败信息，回滚也仓促。
#
# 检查项（任何一项失败都立即中止）：
#   1. /opt 磁盘空间 ≥ 2GB
#   2. java --version 输出含 "21" 或更高
#   3. MySQL 用配置中的 user/password 能 SELECT 1
#   4. REMOTE_DIR 存在且可写（mkdir -p + touch 测试）
#
# 调用方式：在 deploy.py / redeploy.py 的 main() 里 connect_ssh 之后立即调用。

def pre_flight_check(ssh, *, min_disk_gb: float = 2.0, min_java_version: int = 21):
    """部署前置检查 —— 先发现问题，再停机部署。

    任何一项不通过都抛 RuntimeError，让调用方在 try/except 中决定是否继续。
    """
    print("\n== Pre-flight check ==")

    # 1. /opt 磁盘空间
    out, _, code = run(ssh, "df -BG /opt | tail -1 | awk '{print $4}'", silent=True)
    if code != 0 or not out.strip():
        raise RuntimeError(f"无法读取 /opt 磁盘空间（exit={code}, out={out!r}）")
    # df -BG 输出形如 "12G"，去 G 后转 float
    free_str = out.strip().rstrip("G")
    try:
        free_gb = float(free_str)
    except ValueError as e:
        raise RuntimeError(f"无法解析磁盘空间输出: {out!r}") from e
    if free_gb < min_disk_gb:
        raise RuntimeError(
            f"/opt 剩余空间 {free_gb}G 低于阈值 {min_disk_gb}G —— "
            f"先清理 logs/ 或老备份再部署。"
        )
    print(f"  ✓ /opt 剩余 {free_gb}G ≥ {min_disk_gb}G")

    # 2. Java 版本
    # `java -version` 把版本号写到 stderr（历史包袱），需 2>&1 重定向
    out, _, code = run(ssh, "java -version 2>&1 | head -1", silent=True)
    if code != 0:
        raise RuntimeError(f"远端没有 Java 或不在 PATH 中（exit={code}）")
    # 解析 "openjdk version \"21.0.4\" ..." 中的主版本号
    import re
    m = re.search(r'version "(\d+)', out)
    if not m:
        raise RuntimeError(f"无法解析 Java 版本: {out!r}")
    actual_version = int(m.group(1))
    if actual_version < min_java_version:
        raise RuntimeError(
            f"Java 主版本 {actual_version} 低于要求 {min_java_version}"
        )
    print(f"  ✓ Java {actual_version} ≥ {min_java_version}")

    # 3. MySQL 连通性（密码走 MYSQL_PWD 环境变量，避免出现在 ps -ef）
    out, err, code = run(
        ssh,
        f"MYSQL_PWD='{MYSQL_PASS}' mysql -u{MYSQL_USER} -e 'SELECT 1' 2>&1 | tail -3",
        silent=True,
    )
    if code != 0:
        raise RuntimeError(
            f"MySQL 连通失败（exit={code}）：{out.strip() or err.strip()}\n"
            f"请检查 SOCIALFLOW_MYSQL_USER / SOCIALFLOW_MYSQL_PASSWORD 是否正确。"
        )
    print(f"  ✓ MySQL 连通（user={MYSQL_USER}）")

    # 4. REMOTE_DIR 可写
    test_file = f"{REMOTE_DIR}/.preflight-{int(time.time())}.tmp"
    out, err, code = run(
        ssh,
        f"mkdir -p {REMOTE_DIR} && touch {test_file} && rm -f {test_file}",
        silent=True,
    )
    if code != 0:
        raise RuntimeError(
            f"{REMOTE_DIR} 不可写（exit={code}）：{err.strip() or out.strip()}"
        )
    print(f"  ✓ {REMOTE_DIR} 可写")

    print("  ✅ pre-flight all green")
