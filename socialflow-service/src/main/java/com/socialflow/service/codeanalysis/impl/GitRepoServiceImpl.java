package com.socialflow.service.codeanalysis.impl;

import com.socialflow.common.exception.BusinessException;
import com.socialflow.model.entity.RepoAuthCredential;
import com.socialflow.model.vo.RepoCommitVO;
import com.socialflow.service.codeanalysis.GitRepoService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

/**
 * GitRepoService 默认实现 —— 基于 Eclipse JGit。
 *
 * 注意事项：
 *   1. 所有克隆都走 shallow（depth=1 或指定），避免拉大仓库全量历史
 *   2. 克隆到系统 tmp 目录，调用方必须 finally 里调 cleanup
 *   3. Diff 强制按字节截断，防止超大 diff 打爆 LLM token
 *   4. 文件遍历自动跳过二进制目录（node_modules / target / dist / .git 等）
 */
@Slf4j
@Service
public class GitRepoServiceImpl implements GitRepoService {

    /**
     * Clone 超时（秒）—— 30s 内 TCP 握手+HTTPS 必须成功，否则认为不可达。
     * 避免国内机房访问 GitHub 时 JGit 死等几分钟。
     */
    @Value("${socialflow.code-analysis.clone-timeout-seconds:30}")
    private int cloneTimeoutSeconds;

    /**
     * GitHub 国内加速镜像；为空串则关闭重写。
     * 国内常用镜像：kkgithub.com / gh.llkk.cc / ghproxy.com
     * 当直连 https://github.com 失败时自动切换。
     */
    @Value("${socialflow.code-analysis.github-mirror:kkgithub.com}")
    private String githubMirror;

    /** 关闭自动镜像重写 */
    @Value("${socialflow.code-analysis.mirror-enabled:true}")
    private boolean mirrorEnabled;

    private static final Set<String> DEFAULT_EXCLUDE = Set.of(
            "node_modules", "target", "dist", ".git", ".idea", ".vscode",
            "build", "out", "coverage", "__pycache__", "logs"
    );

    /** 判断是否源码文件（扩展名白名单）*/
    private static final Map<String, String> LANG_BY_EXT = Map.ofEntries(
            Map.entry("java", "Java"),
            Map.entry("kt", "Kotlin"),
            Map.entry("vue", "Vue"),
            Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"),
            Map.entry("js", "JavaScript"),
            Map.entry("jsx", "JavaScript"),
            Map.entry("py", "Python"),
            Map.entry("go", "Go"),
            Map.entry("rs", "Rust"),
            Map.entry("c", "C"),
            Map.entry("cpp", "C++"),
            Map.entry("h", "C/C++"),
            Map.entry("sql", "SQL"),
            Map.entry("sh", "Shell"),
            Map.entry("yml", "YAML"),
            Map.entry("yaml", "YAML"),
            Map.entry("xml", "XML"),
            Map.entry("html", "HTML"),
            Map.entry("css", "CSS"),
            Map.entry("scss", "SCSS")
    );

    /** README / 构建配置 / 入口 —— 项目概览必读 */
    private static final List<String> KEY_FILE_PATTERNS = List.of(
            "README.md", "README.MD", "readme.md",
            "pom.xml", "package.json", "build.gradle", "build.gradle.kts",
            "requirements.txt", "Cargo.toml", "go.mod",
            "docker-compose.yml", "docker-compose.yaml", "Dockerfile",
            ".env.example"
    );

    @Override
    public File shallowClone(String gitUrl, String branch, Integer depth) {
        return shallowClone(gitUrl, branch, depth, null);
    }

    @Override
    public File shallowClone(String gitUrl, String branch, Integer depth, RepoAuthCredential credential) {
        int cloneDepth = (depth == null || depth <= 0) ? 1 : depth;
        String primaryUrl = gitUrl;
        String fallbackUrl = mirrorUrl(gitUrl);
        boolean hasCredential = credential != null;

        // ============ 策略说明 ============
        // 国内机房直连 github.com 是网络层超时（HTTPS 墙），与有没有 Token 无关。
        // Token 只解决认证，不解决网络可达。所以 GitHub 类地址永远优先走镜像：
        //   - kkgithub.com 作为全量 GitHub 代理，Basic Auth / Token 透传能正常工作
        //   - 镜像失败才回退原 URL（覆盖企业内网可直连场景 / 镜像临时故障）

        Exception mirrorError = null;
        if (fallbackUrl != null && !fallbackUrl.equals(primaryUrl)) {
            log.info("[Git] 优先走镜像: {} → {} (hasCredential={})",
                    primaryUrl, fallbackUrl, hasCredential);
            try {
                return doClone(fallbackUrl, branch, cloneDepth, credential);
            } catch (Exception e) {
                mirrorError = e;
                log.warn("[Git] 镜像克隆失败 ({}), 尝试回退原 URL: {}",
                        fallbackUrl, truncateMsg(e.getMessage(), 160));
            }
        }

        // 镜像失败或非 GitHub host → 尝试原 URL（可能是企业 GitLab / 自建 Gitea）
        try {
            log.info("[Git] 使用原 URL: {} (hasCredential={})", primaryUrl, hasCredential);
            return doClone(primaryUrl, branch, cloneDepth, credential);
        } catch (Exception e) {
            String friendly = buildFriendlyError(primaryUrl, e, hasCredential);
            if (mirrorError != null) {
                friendly += "\n（镜像 " + fallbackUrl + " 也失败：" +
                        truncateMsg(mirrorError.getMessage(), 120) + "）";
            }
            throw new BusinessException(friendly);
        }
    }

    private static String truncateMsg(String s, int n) {
        if (s == null) return "null";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    /** 单次克隆尝试，带超时 + 可选凭证 */
    private File doClone(String url, String branch, int cloneDepth, RepoAuthCredential credential) throws Exception {
        File target;
        try {
            target = Files.createTempDirectory("sfca-" + System.currentTimeMillis() + "-").toFile();
        } catch (IOException e) {
            throw new BusinessException("创建临时目录失败: " + e.getMessage());
        }
        log.info("[Git] shallow clone url={} branch={} depth={} timeout={}s → {}",
                url, branch, cloneDepth, cloneTimeoutSeconds, target);

        try {
            var cloneCmd = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(target)
                    .setDepth(cloneDepth)
                    .setCloneAllBranches(false)
                    .setTimeout(cloneTimeoutSeconds);  // JGit 的连接/读取超时（秒）
            if (branch != null && !branch.isBlank()) {
                cloneCmd.setBranch(branch);
                cloneCmd.setBranchesToClone(List.of("refs/heads/" + branch));
            }
            if (credential != null && credential.getPlainToken() != null) {
                // plainToken 是 transient + JsonIgnore + ToString.Exclude，确保异常栈/日志不会泄漏
                cloneCmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                        credential.getUsername(), credential.getPlainToken()));
            }
            try (Git git = cloneCmd.call()) {
                log.info("[Git] cloned OK, head={}", git.getRepository().resolve("HEAD"));
            }
            return target;
        } catch (Exception e) {
            cleanup(target);
            throw e;
        }
    }

    /** 把 github.com 地址改写成镜像地址；非 github 或镜像关闭返回 null */
    private String mirrorUrl(String gitUrl) {
        if (!mirrorEnabled || githubMirror == null || githubMirror.isBlank()) return null;
        if (gitUrl == null) return null;
        // 只重写 HTTPS 形式的 github.com
        if (gitUrl.startsWith("https://github.com/")) {
            return "https://" + githubMirror + gitUrl.substring("https://github.com".length());
        }
        if (gitUrl.startsWith("http://github.com/")) {
            return "https://" + githubMirror + gitUrl.substring("http://github.com".length());
        }
        return null;
    }

    private String buildFriendlyError(String url, Exception e, boolean withCredential) {
        String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String lower = cause.toLowerCase();
        boolean looksNetwork = lower.contains("timed out") || lower.contains("connection")
                || lower.contains("read timed out") || lower.contains("unreachable");
        boolean looksAuth = lower.contains("not authorized") || lower.contains("authentication")
                || lower.contains(" 401") || lower.contains(" 403") || lower.contains("unauthorized");

        if (looksAuth) {
            if (withCredential) {
                return "克隆失败：凭证认证失败（HTTP 401/403），请在"
                        + "「代码分析 → 仓库凭证」里检查 Token 是否正确或已过期。原始错误：" + truncate(cause, 200);
            }
            return "克隆失败：该仓库需要认证。请在「代码分析 → 仓库凭证」里添加对应 Git Host 的凭证，"
                    + "然后重新触发分析。原始错误：" + truncate(cause, 200);
        }
        if (looksNetwork && url.contains("github.com")) {
            return "克隆 GitHub 仓库失败：服务器无法直连 github.com（国内机房常见）。已尝试镜像 "
                    + githubMirror + " 仍失败。建议：1) 在「仓库凭证」里配置 GitHub PAT（带 token 多数时候能穿透）；"
                    + "2) 换成 Gitee 镜像地址；3) 管理员在服务器配置 HTTP/SOCKS 代理。原始错误：" + truncate(cause, 200);
        }
        if (looksNetwork) {
            return "克隆失败：网络无法到达 " + url + "。如果是公司内部仓库，请检查服务器能否访问该 host。原始错误：" + truncate(cause, 200);
        }
        return "克隆仓库失败: " + truncate(cause, 300);
    }

    private static String truncate(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n) + "...";
    }

    @Override
    public List<RepoCommitVO> listCommits(File repoDir, int limit) {
        List<RepoCommitVO> result = new ArrayList<>();
        try (Git git = Git.open(repoDir)) {
            Iterable<RevCommit> commits = git.log().setMaxCount(Math.max(1, limit)).call();
            for (RevCommit c : commits) {
                RepoCommitVO vo = new RepoCommitVO();
                String sha = c.getName();
                vo.setSha(sha);
                vo.setShortSha(sha.substring(0, 7));
                vo.setAuthor(c.getAuthorIdent() != null ? c.getAuthorIdent().getName() : null);
                vo.setEmail(c.getAuthorIdent() != null ? c.getAuthorIdent().getEmailAddress() : null);
                vo.setCommitTime(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(c.getCommitTime()), ZoneId.systemDefault()));
                String full = c.getFullMessage();
                int nl = full.indexOf('\n');
                vo.setSubject(nl > 0 ? full.substring(0, nl) : full);
                // 简化：changedFiles / additions / deletions 在审查入口再算，避免全量计算
                result.add(vo);
            }
        } catch (Exception e) {
            throw new BusinessException("读取提交列表失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public String readCommitDiff(File repoDir, String commitSha, int maxBytes) {
        try (Git git = Git.open(repoDir)) {
            Repository repo = git.getRepository();
            ObjectId target = repo.resolve(commitSha);
            if (target == null) {
                throw new BusinessException("commitSha 无法解析: " + commitSha);
            }
            try (RevWalk walk = new RevWalk(repo)) {
                RevCommit commit = walk.parseCommit(target);
                RevCommit parent = commit.getParentCount() > 0
                        ? walk.parseCommit(commit.getParent(0).getId())
                        : null;
                return formatDiff(repo, parent, commit, maxBytes);
            }
        } catch (Exception e) {
            throw new BusinessException("读取 commit diff 失败: " + e.getMessage());
        }
    }

    @Override
    public String readDiff(File repoDir, String baseRef, String headRef, int maxBytes) {
        try (Git git = Git.open(repoDir)) {
            Repository repo = git.getRepository();
            ObjectId base = repo.resolve(baseRef);
            ObjectId head = repo.resolve(headRef);
            if (base == null || head == null) {
                throw new BusinessException("ref 无法解析: " + baseRef + " / " + headRef);
            }
            try (RevWalk walk = new RevWalk(repo)) {
                RevCommit baseC = walk.parseCommit(base);
                RevCommit headC = walk.parseCommit(head);
                return formatDiff(repo, baseC, headC, maxBytes);
            }
        } catch (Exception e) {
            throw new BusinessException("读取 diff 失败: " + e.getMessage());
        }
    }

    private String formatDiff(Repository repo, RevCommit base, RevCommit head, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter df = new DiffFormatter(out)) {
            df.setRepository(repo);
            df.setDetectRenames(true);
            List<DiffEntry> entries;
            if (base == null) {
                // initial commit: 与空树对比
                try (RevWalk walk = new RevWalk(repo)) {
                    CanonicalTreeParser emptyTree = new CanonicalTreeParser();
                    CanonicalTreeParser newTree = new CanonicalTreeParser();
                    newTree.reset(repo.newObjectReader(), head.getTree());
                    entries = df.scan(emptyTree, newTree);
                }
            } else {
                entries = df.scan(base.getTree(), head.getTree());
            }
            for (DiffEntry e : entries) {
                df.format(e);
                if (out.size() > maxBytes) {
                    out.write("\n\n...[diff 已截断，达字节上限]...\n".getBytes(StandardCharsets.UTF_8));
                    break;
                }
            }
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length > maxBytes) {
            byte[] truncated = Arrays.copyOf(bytes, maxBytes);
            return new String(truncated, StandardCharsets.UTF_8) + "\n\n...[diff 已截断]...";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public Map<String, Long> scanLanguageStats(File repoDir, List<String> excludeDirs) {
        Set<String> excludes = merge(excludeDirs);
        Map<String, Long> result = new HashMap<>();
        Path root = repoDir.toPath();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(root, p, excludes))
                    .forEach(p -> {
                        String ext = extOf(p.getFileName().toString());
                        String lang = LANG_BY_EXT.get(ext);
                        if (lang == null) return;
                        long lines = safeCountLines(p);
                        result.merge(lang, lines, Long::sum);
                    });
        } catch (IOException e) {
            log.warn("[Git] scanLanguageStats failed: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public String readKeyFiles(File repoDir, int maxBytes) {
        Path root = repoDir.toPath();
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (String name : KEY_FILE_PATTERNS) {
            Path p = root.resolve(name);
            if (!Files.exists(p)) continue;
            try {
                byte[] content = Files.readAllBytes(p);
                int remain = maxBytes - total;
                if (remain <= 0) break;
                int take = Math.min(content.length, remain);
                sb.append("\n\n===== ").append(name).append(" =====\n");
                sb.append(new String(content, 0, take, StandardCharsets.UTF_8));
                total += take;
                if (take < content.length) {
                    sb.append("\n...[已截断]...\n");
                }
            } catch (IOException ignore) { /* 跳过 */ }
        }
        return sb.toString();
    }

    @Override
    public String buildTreeView(File repoDir, int maxDepth, List<String> excludeDirs) {
        Set<String> excludes = merge(excludeDirs);
        StringBuilder sb = new StringBuilder();
        Path root = repoDir.toPath();
        walkTree(sb, root, root, 0, maxDepth, excludes);
        return sb.toString();
    }

    private void walkTree(StringBuilder sb, Path root, Path curr, int depth, int maxDepth, Set<String> excludes) {
        if (depth > maxDepth) return;
        try (Stream<Path> stream = Files.list(curr)) {
            List<Path> sorted = stream
                    .filter(p -> !excludes.contains(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path p : sorted) {
                sb.append("  ".repeat(depth))
                  .append(Files.isDirectory(p) ? "📁 " : "📄 ")
                  .append(p.getFileName())
                  .append('\n');
                if (Files.isDirectory(p)) {
                    walkTree(sb, root, p, depth + 1, maxDepth, excludes);
                }
            }
        } catch (IOException ignore) { /* 跳过 */ }
    }

    @Override
    public void cleanup(File repoDir) {
        if (repoDir == null || !repoDir.exists()) return;
        try {
            deleteRecursive(repoDir.toPath());
        } catch (IOException e) {
            log.warn("[Git] cleanup failed: {} ({})", repoDir, e.getMessage());
        }
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.list(p)) {
                for (Path c : s.toList()) deleteRecursive(c);
            }
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // Windows 上 .git/pack 有时会被 JGit 内部锁占用，重试一次
            try {
                Thread.sleep(50);
                Files.deleteIfExists(p);
            } catch (Exception ignore) { /* */ }
        }
    }

    private static Set<String> merge(List<String> custom) {
        if (custom == null || custom.isEmpty()) return DEFAULT_EXCLUDE;
        Set<String> s = new HashSet<>(DEFAULT_EXCLUDE);
        s.addAll(custom);
        return s;
    }

    private static boolean isExcluded(Path root, Path p, Set<String> excludes) {
        Path rel = root.relativize(p);
        for (Path seg : rel) {
            if (excludes.contains(seg.toString())) return true;
        }
        return false;
    }

    private static String extOf(String filename) {
        int i = filename.lastIndexOf('.');
        return (i <= 0 || i == filename.length() - 1) ? "" : filename.substring(i + 1).toLowerCase();
    }

    private static long safeCountLines(Path p) {
        try (Stream<String> s = Files.lines(p, StandardCharsets.UTF_8)) {
            return s.count();
        } catch (Exception e) {
            return 0L;
        }
    }

    // ==================== 全量分析实现 ====================

    @Override
    public Map<String, List<SourceFile>> scanSourcesByModule(File repoDir, List<String> excludeDirs, int perFileMaxBytes) {
        Set<String> excludes = merge(excludeDirs);
        Map<String, List<SourceFile>> result = new LinkedHashMap<>();
        Path root = repoDir.toPath();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(root, p, excludes))
                    .filter(p -> {
                        String ext = extOf(p.getFileName().toString());
                        return LANG_BY_EXT.containsKey(ext);
                    })
                    .sorted()
                    .forEach(p -> {
                        Path rel = root.relativize(p);
                        String moduleName = rel.getNameCount() > 1 ? rel.getName(0).toString() : "ROOT";
                        byte[] bytes;
                        try {
                            bytes = Files.readAllBytes(p);
                        } catch (IOException e) {
                            // 黄山版 2.2.2：catch 必须记日志。
                            // 单文件读失败不影响整体扫描（其他文件继续），所以仅 warn 后跳过。
                            log.warn("[Git] 读取文件失败，跳过: path={}, err={}", p, e.getMessage());
                            return;
                        }
                        // 按字节截断保留头部，保留完整行结尾
                        String content;
                        if (bytes.length <= perFileMaxBytes) {
                            content = new String(bytes, StandardCharsets.UTF_8);
                        } else {
                            content = new String(bytes, 0, perFileMaxBytes, StandardCharsets.UTF_8)
                                    + "\n// ... [文件被截断，原大小 " + bytes.length + " 字节] ...";
                        }
                        int lines = (int) safeCountLines(p);
                        result.computeIfAbsent(moduleName, k -> new ArrayList<>())
                              .add(new SourceFile(rel.toString().replace('\\', '/'), content, lines));
                    });
        } catch (IOException e) {
            log.warn("[Git] scanSourcesByModule failed: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public List<FileDiff> readCommitDiffByFile(File repoDir, String commitSha) {
        try (Git git = Git.open(repoDir)) {
            Repository repo = git.getRepository();
            ObjectId target = repo.resolve(commitSha);
            if (target == null) throw new BusinessException("commitSha 无法解析: " + commitSha);
            try (RevWalk walk = new RevWalk(repo)) {
                RevCommit commit = walk.parseCommit(target);
                RevCommit parent = commit.getParentCount() > 0
                        ? walk.parseCommit(commit.getParent(0).getId()) : null;
                return formatDiffByFile(repo, parent, commit);
            }
        } catch (Exception e) {
            throw new BusinessException("读取 commit diff 失败: " + e.getMessage());
        }
    }

    @Override
    public List<FileDiff> readDiffByFile(File repoDir, String baseRef, String headRef) {
        try (Git git = Git.open(repoDir)) {
            Repository repo = git.getRepository();
            ObjectId base = repo.resolve(baseRef);
            ObjectId head = repo.resolve(headRef);
            if (base == null || head == null) {
                throw new BusinessException("ref 无法解析: " + baseRef + " / " + headRef);
            }
            try (RevWalk walk = new RevWalk(repo)) {
                return formatDiffByFile(repo, walk.parseCommit(base), walk.parseCommit(head));
            }
        } catch (Exception e) {
            throw new BusinessException("读取 diff 失败: " + e.getMessage());
        }
    }

    /** 把 diff 按文件切分，每个文件一条 FileDiff，不截断 */
    private List<FileDiff> formatDiffByFile(Repository repo, RevCommit base, RevCommit head) throws IOException {
        List<FileDiff> result = new ArrayList<>();
        List<DiffEntry> entries;
        // scan 一次 DiffEntry 列表
        try (DiffFormatter scanner = new DiffFormatter(ByteArrayOutputStream.nullOutputStream())) {
            scanner.setRepository(repo);
            scanner.setDetectRenames(true);
            if (base == null) {
                try (RevWalk walk = new RevWalk(repo)) {
                    CanonicalTreeParser emptyTree = new CanonicalTreeParser();
                    CanonicalTreeParser newTree = new CanonicalTreeParser();
                    newTree.reset(repo.newObjectReader(), head.getTree());
                    entries = scanner.scan(emptyTree, newTree);
                }
            } else {
                entries = scanner.scan(base.getTree(), head.getTree());
            }
        }
        // 对每个 DiffEntry 单独格式化（带 ±5 行上下文 —— 方案 D）
        for (DiffEntry e : entries) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter df = new DiffFormatter(out)) {
                df.setRepository(repo);
                df.setDetectRenames(true);
                // 黄山版审查方案 D：上下文行从默认的 3 调到 5，让 LLM 看清违规所在的更大上下文
                // 既能避免"只看几行不知道整体"的误判，又不会膨胀 prompt 太多
                df.setContext(5);
                df.format(e);
            }
            byte[] bytes = out.toByteArray();
            String path = e.getNewPath() != null && !"/dev/null".equals(e.getNewPath())
                    ? e.getNewPath() : e.getOldPath();
            result.add(new FileDiff(path, new String(bytes, StandardCharsets.UTF_8), bytes.length));
        }
        return result;
    }
}
