#!/usr/bin/env python3
"""
SocialFlow 完整部署：MySQL 备份 + jar + dist + 启动 + 健康检查 + Smoke Test。

如果只需替换 jar 而不动数据库和前端，请改用 redeploy.py。

凭证（SSH/MySQL 密码、生产 IP）必须通过环境变量注入，详见 ops_common.py 顶部注释。

失败时手动回滚（具体 HOST / 凭证以本地环境变量为准）：
  ssh $SOCIALFLOW_SSH_USER@$SOCIALFLOW_SSH_HOST
  kill <new_pid>
  cp $SOCIALFLOW_REMOTE_DIR/backups/<timestamp>/socialflow.jar.bak $SOCIALFLOW_REMOTE_DIR/socialflow.jar
  mysql -u$SOCIALFLOW_MYSQL_USER -p"$SOCIALFLOW_MYSQL_PASSWORD" $SOCIALFLOW_MYSQL_DB \\
        < $SOCIALFLOW_REMOTE_DIR/backups/<timestamp>/mysql.sql
  nohup java -jar $SOCIALFLOW_REMOTE_DIR/socialflow.jar > $SOCIALFLOW_REMOTE_DIR/logs/app.log 2>&1 &
"""
import os
import sys
from datetime import datetime
from pathlib import Path

from paramiko import SFTPClient

from ops_common import (
    HOST, USER, MYSQL_USER, MYSQL_PASS, MYSQL_DB, REMOTE_DIR, REMOTE_WEB,
    change_to_project_root, connect_ssh, kill_backend, pre_flight_check,
    run, smoke_test, start_backend, tail_flyway_log, utf8_stdout, wait_health,
)

utf8_stdout()

LOCAL_JAR = Path("socialflow-admin/target/socialflow-admin.jar")
LOCAL_DIST = Path("socialflow-ui/dist")
TS = datetime.now().strftime("%Y%m%d-%H%M%S")
REMOTE_BACKUP_DIR = f"{REMOTE_DIR}/backups/{TS}"


def upload(sftp: SFTPClient, local: Path, remote: str):
    size = local.stat().st_size
    print(f"  uploading {local} ({size / 1024 / 1024:.1f} MB) → {remote}")
    sftp.put(str(local), remote)


def upload_dir(sftp: SFTPClient, local_dir: Path, remote_dir: str):
    for root, _, files in os.walk(local_dir):
        rel = Path(root).relative_to(local_dir)
        remote_sub = remote_dir if str(rel) == "." else f"{remote_dir}/{rel.as_posix()}"
        try:
            sftp.stat(remote_sub)
        except FileNotFoundError:
            sftp.mkdir(remote_sub)
        for f in files:
            sftp.put(str(Path(root) / f), f"{remote_sub}/{f}")


def main():
    print("== 检查本地产物 ==")
    if not LOCAL_JAR.exists():
        print(f"❌ {LOCAL_JAR} 不存在")
        sys.exit(1)
    if not (LOCAL_DIST / "index.html").exists():
        print(f"❌ {LOCAL_DIST}/index.html 不存在")
        sys.exit(1)
    print(f"  ✓ jar: {LOCAL_JAR} ({LOCAL_JAR.stat().st_size / 1024 / 1024:.1f} MB)")
    print(f"  ✓ dist: {LOCAL_DIST}")

    print(f"\n== 连接 {USER}@{HOST} ==")
    ssh, sftp = connect_ssh()

    try:
        # 先发现环境问题再动停机：磁盘 / Java / MySQL 任一不达标都直接退出
        try:
            pre_flight_check(ssh)
        except RuntimeError as e:
            print(f"❌ Pre-flight check 失败：{e}")
            sys.exit(3)

        print(f"\n== 准备备份目录 {REMOTE_BACKUP_DIR} ==")
        run(ssh, f"mkdir -p {REMOTE_BACKUP_DIR}")

        print("\n== MySQL dump 备份 ==")
        # check=True 让 mysqldump 失败立即终止部署，避免拿不到备份就贸然停服。
        # 密码通过 MYSQL_PWD 环境变量注入而不是命令行参数，避免在远端 ps -ef 里被看到。
        run(
            ssh,
            f"MYSQL_PWD='{MYSQL_PASS}' mysqldump -u{MYSQL_USER} "
            f"--single-transaction --routines {MYSQL_DB} "
            f"> {REMOTE_BACKUP_DIR}/mysql.sql",
            check=True,
        )
        out, _, _ = run(ssh, f"ls -lh {REMOTE_BACKUP_DIR}/mysql.sql | awk '{{print $5}}'")
        print(f"  ✓ mysql.sql 大小 {out.strip()}")

        print("\n== 停止现有后端进程 ==")
        kill_backend(ssh)

        print("\n== 备份旧 jar + 旧 dist ==")
        run(ssh, f"[ -f {REMOTE_DIR}/socialflow.jar ] && cp {REMOTE_DIR}/socialflow.jar {REMOTE_BACKUP_DIR}/socialflow.jar.bak || true")
        run(ssh, f"[ -d {REMOTE_WEB} ] && tar czf {REMOTE_BACKUP_DIR}/web.tgz -C {REMOTE_DIR} web || true")
        out, _, _ = run(ssh, f"ls -lh {REMOTE_BACKUP_DIR}/ | tail -5")
        print(out)

        print("\n== 上传新 jar ==")
        upload(sftp, LOCAL_JAR, f"{REMOTE_DIR}/socialflow.jar")
        run(ssh, f"chmod 644 {REMOTE_DIR}/socialflow.jar")

        print("\n== 清理旧 web + 上传新 dist ==")
        run(ssh, f"rm -rf {REMOTE_WEB}")
        run(ssh, f"mkdir -p {REMOTE_WEB}")
        upload_dir(sftp, LOCAL_DIST, REMOTE_WEB)
        out, _, _ = run(ssh, f"ls {REMOTE_WEB} | head -10")
        print(f"  dist 内容: {out.strip()[:200]}")

        print("\n== 启动后端（SPRING_PROFILES_ACTIVE=prod）==")
        new_pid = start_backend(ssh)
        print(f"  新进程 PID: {new_pid}")

        if not wait_health(ssh, max_wait=180):
            print("\n❌ 启动超时（180s）。查看最后 60 行日志：")
            out, _, _ = run(ssh, f"tail -60 {REMOTE_DIR}/logs/app.log")
            print(out)
            sys.exit(2)

        tail_flyway_log(ssh)
        smoke_test(ssh)

        print("\n✅ 部署完成！")
        print(f"   备份目录：{REMOTE_BACKUP_DIR}")
        print(f"   访问：http://{HOST}")

    finally:
        sftp.close()
        ssh.close()


if __name__ == "__main__":
    change_to_project_root()
    main()
