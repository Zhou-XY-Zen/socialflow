"""快速检查：后端进程 + /actuator/health + 最近日志。"""
from ops_common import REMOTE_DIR, connect_ssh, run, utf8_stdout

utf8_stdout()

ssh, sftp = connect_ssh(timeout=10)
try:
    run(ssh, "pgrep -af 'java.*socialflow.jar' | head")
    run(ssh, "curl -s -o /dev/null -w 'HTTP=%{http_code}\\n' http://127.0.0.1:8080/actuator/health")
    run(ssh, f"tail -15 {REMOTE_DIR}/logs/app.log")
finally:
    sftp.close()
    ssh.close()
