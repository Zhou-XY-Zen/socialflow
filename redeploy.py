#!/usr/bin/env python3
"""精简重新部署：只替换 jar + 重启 + 验证。MySQL/dist 已经由 deploy.py 处理过。"""
import sys
from pathlib import Path

from ops_common import (
    HOST, REMOTE_DIR, change_to_project_root, connect_ssh, kill_backend,
    pre_flight_check, run, smoke_test, start_backend, tail_flyway_log,
    utf8_stdout, wait_health,
)

utf8_stdout()

LOCAL_JAR = Path("socialflow-admin/target/socialflow-admin.jar")


def main():
    change_to_project_root()
    if not LOCAL_JAR.exists():
        print(f"❌ {LOCAL_JAR} 不存在")
        sys.exit(1)

    ssh, sftp = connect_ssh()

    try:
        # redeploy 不动 DB，磁盘要求降到 0.5GB（仅放新 jar 和日志增量）
        try:
            pre_flight_check(ssh, min_disk_gb=0.5)
        except RuntimeError as e:
            print(f"❌ Pre-flight check 失败：{e}")
            sys.exit(3)

        print("== 清理残留进程 ==")
        kill_backend(ssh, force_grace=2)

        print("\n== 上传新 jar ==")
        print(f"  {LOCAL_JAR} ({LOCAL_JAR.stat().st_size / 1024 / 1024:.1f} MB)")
        sftp.put(str(LOCAL_JAR), f"{REMOTE_DIR}/socialflow.jar")
        run(ssh, f"chmod 644 {REMOTE_DIR}/socialflow.jar")

        print("\n== 启动后端 (prod profile) ==")
        pid = start_backend(ssh)
        print(f"  PID: {pid}")

        if not wait_health(ssh, max_wait=180):
            print("\n❌ 启动超时。最后 60 行日志：")
            out, _, _ = run(ssh, f"tail -80 {REMOTE_DIR}/logs/app.log")
            print(out)
            sys.exit(2)

        tail_flyway_log(ssh, lines=15)
        smoke_test(ssh)

        print(f"\n✅ 部署完成！访问 http://{HOST}")

    finally:
        sftp.close()
        ssh.close()


if __name__ == "__main__":
    main()
