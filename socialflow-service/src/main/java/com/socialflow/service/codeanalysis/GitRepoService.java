package com.socialflow.service.codeanalysis;

import com.socialflow.model.vo.RepoCommitVO;

import java.io.File;
import java.util.List;

/**
 * Git 仓库操作门面 —— 屏蔽 JGit 底层细节，提供代码分析所需的高层操作：
 *   - 克隆仓库到临时目录
 *   - 读取 commit 列表
 *   - 读取某次提交的 diff 文本
 *   - 读取两个 ref 之间的 diff
 *   - 遍历工作树中指定扩展名的源文件
 *
 * 实现：org.eclipse.jgit 纯 Java 客户端，无需系统 git。
 */
public interface GitRepoService {

    /**
     * 浅克隆（shallow clone）仓库到临时目录。
     *
     * @param gitUrl 仓库 URL（HTTPS）
     * @param branch 分支名，null 则默认
     * @param depth  历史深度；null 或 <=0 走默认 1
     * @return 克隆目录（调用方完成后应调用 {@link #cleanup(File)} 清理）
     */
    File shallowClone(String gitUrl, String branch, Integer depth);

    /**
     * 读取最近 N 条提交。
     */
    List<RepoCommitVO> listCommits(File repoDir, int limit);

    /**
     * 读取单次提交的 diff 文本。
     *
     * @param repoDir   已克隆的本地仓库路径
     * @param commitSha 可以是完整 SHA 或前缀
     * @param maxBytes  diff 截断字节上限，避免 token 溢出
     */
    String readCommitDiff(File repoDir, String commitSha, int maxBytes);

    /**
     * 读取两个 ref 之间的 diff。
     */
    String readDiff(File repoDir, String baseRef, String headRef, int maxBytes);

    /**
     * 扫描仓库统计语言行数占比。
     *
     * @return 形如 {"Java": 12345, "Vue": 5000, ...} 的映射
     */
    java.util.Map<String, Long> scanLanguageStats(File repoDir, java.util.List<String> excludeDirs);

    /**
     * 读取"关键文件"—— README、pom.xml、package.json、application*.yml、入口主类等，
     * 拼接后返回，供 LLM 生成项目概览。
     *
     * @param maxBytes 总字节上限（粗略控制 token）
     */
    String readKeyFiles(File repoDir, int maxBytes);

    /**
     * 生成仓库目录树字符串（最多 N 层）。
     */
    String buildTreeView(File repoDir, int maxDepth, java.util.List<String> excludeDirs);

    /**
     * 删除临时克隆目录。
     */
    void cleanup(File repoDir);
}
