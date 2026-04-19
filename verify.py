"""部署后验证：Flyway migration + 新列 + 端点 + 内存 + 自定义指标。"""
from ops_common import (
    MYSQL_PASS, MYSQL_USER, REMOTE_DIR, connect_ssh, run, smoke_test,
    utf8_stdout,
)

utf8_stdout()

ssh, sftp = connect_ssh(timeout=10)

VERIFY_ENDPOINTS = [
    ("Nginx 首页", "http://127.0.0.1/"),
    ("/actuator/health", "http://127.0.0.1:8080/actuator/health"),
    ("/actuator/prometheus", "http://127.0.0.1:8080/actuator/prometheus"),
    ("/actuator/flyway", "http://127.0.0.1:8080/actuator/flyway"),
    ("/actuator/circuitbreakers", "http://127.0.0.1:8080/actuator/circuitbreakers"),
    ("/api/v1/auth/me (预期 401)", "http://127.0.0.1:8080/api/v1/auth/me"),
    ("/api/v1/dashboard/overview (预期 401)", "http://127.0.0.1:8080/api/v1/dashboard/overview"),
]


def r(cmd, label=None):
    if label:
        print(f"\n=== {label} ===")
    run(ssh, cmd)


try:
    r(
        f"mysql -u{MYSQL_USER} -p{MYSQL_PASS} -e \"USE socialflow; SELECT installed_rank, version, script, success FROM flyway_schema_history ORDER BY installed_rank;\"",
        "Flyway schema_history 表",
    )
    r(
        f"grep -iE 'flyway|migrating|baseline|schema_history' {REMOTE_DIR}/logs/app.log | head -30",
        "Flyway 启动日志",
    )
    r(
        f"mysql -u{MYSQL_USER} -p{MYSQL_PASS} -e \"USE socialflow; SHOW COLUMNS FROM content LIKE 'version'; SHOW COLUMNS FROM eval_task LIKE 'p_value'; SHOW COLUMNS FROM media_asset LIKE 'sha256';\"",
        "Wave 4 新增列验证",
    )
    r(
        f"mysql -u{MYSQL_USER} -p{MYSQL_PASS} -e \"USE socialflow; SHOW TABLES LIKE 'image_asset_cache';\"",
        "image_asset_cache 新表",
    )

    smoke_test(ssh, VERIFY_ENDPOINTS)

    r(
        "ps -o pid,rss,cmd -p $(pgrep -f 'java.*socialflow.jar' | head -1)",
        "后端进程内存占用",
    )
    r(
        "curl -s http://127.0.0.1:8080/actuator/prometheus | grep -E 'sf_llm|sf_publish|sf_image' | head -20",
        "自定义 Prometheus 指标（Wave 2.1）",
    )
finally:
    sftp.close()
    ssh.close()

print("\n✅ 验证完成")
