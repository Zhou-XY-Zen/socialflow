#!/usr/bin/env python3
"""
部署前探查 —— 看服务器上 socialflow 现在是怎么跑的，
以便规划后续的 jar 替换 + 前端 dist 同步 + 服务重启。
"""
from ops_common import HOST, MYSQL_PASS, MYSQL_USER, USER, connect_ssh, run, utf8_stdout

utf8_stdout()


def r(ssh, cmd, label=None):
    if label:
        print(f"\n=== {label} ===")
    run(ssh, cmd)


def main():
    print(f"Connecting to {USER}@{HOST}...")
    ssh, sftp = connect_ssh(timeout=10)
    print("OK")

    try:
        r(ssh, "ls -la /opt/socialflow/ 2>/dev/null | head -30", "部署目录")
        r(ssh, "ps -ef | grep -E 'socialflow|java.*jar' | grep -v grep", "运行中的 Java 进程")
        r(ssh, "systemctl list-units --type=service --all 2>/dev/null | grep -i socialflow", "systemd 服务")
        r(ssh, "ls /etc/systemd/system/ 2>/dev/null | grep -i socialflow", "systemd unit 文件")
        r(ssh, "ls /etc/nginx/conf.d/ 2>/dev/null && ls /etc/nginx/sites-enabled/ 2>/dev/null", "Nginx 配置路径")
        r(ssh, "grep -l socialflow /etc/nginx/conf.d/*.conf /etc/nginx/sites-enabled/* 2>/dev/null", "Nginx 里和 socialflow 相关的配置文件")
        r(ssh, "java -version 2>&1", "Java 版本")
        r(ssh, "systemctl is-active mysql mysqld 2>/dev/null | head -3", "MySQL 服务状态")
        r(
            ssh,
            f"mysql -u{MYSQL_USER} -p{MYSQL_PASS} -e 'SELECT DATABASE();USE socialflow;SHOW TABLES LIKE \"flyway%\";' 2>&1 | tail -10",
            "MySQL 连通 + Flyway 历史表",
        )
        r(ssh, "df -h /opt 2>/dev/null", "/opt 磁盘空间")
    finally:
        sftp.close()
        ssh.close()


if __name__ == "__main__":
    main()
