<!--
  Rules.vue —— 阿里巴巴 Java 开发手册规约库（内置精选）
-->
<script setup lang="ts">
import { ref, computed } from 'vue'

interface Rule {
  code: string
  title: string
  level: 'MANDATORY' | 'RECOMMENDED' | 'REFERENCE'
  category: string
  content: string
  exampleBad?: string
  exampleGood?: string
}

// 内置精选规约（一期硬编码，后续可走数据库）
const rules: Rule[] = [
  { code: '1.1.1', title: '命名不能以下划线或美元符号开始/结束', level: 'MANDATORY', category: '编程规约-命名',
    content: '所有变量、方法、包名不允许以下划线或美元符号开始或结束。',
    exampleBad: 'String _name;\nString $value;', exampleGood: 'String name;\nString value;' },
  { code: '1.1.4', title: '类名使用 UpperCamelCase 风格', level: 'MANDATORY', category: '编程规约-命名',
    content: '类名使用 UpperCamelCase 风格，必须遵从驼峰形式，但 DO / BO / DTO / VO / AO / PO / UID 等除外。',
    exampleBad: 'class user_info {}', exampleGood: 'class UserInfo {}' },
  { code: '1.1.9', title: '接口不加 I 前缀', level: 'MANDATORY', category: '编程规约-命名',
    content: '接口不需要加"I"前缀。接口的实现类才需要用 Impl 做后缀。',
    exampleBad: 'interface IUserService', exampleGood: 'interface UserService' },
  { code: '1.1.11', title: '常量使用 UPPER_UNDERSCORE', level: 'MANDATORY', category: '编程规约-命名',
    content: '常量命名全部大写，单词间用下划线隔开，力求语义表达完整清楚。',
    exampleBad: 'int maxCount = 100;', exampleGood: 'static final int MAX_COUNT = 100;' },
  { code: '1.3.2', title: 'long 字面量用大写 L', level: 'MANDATORY', category: '编程规约-常量',
    content: 'long 或 Long 初始赋值时，必须使用大写 L，不能小写 l。小写容易和数字 1 混淆。',
    exampleBad: 'long id = 2l;', exampleGood: 'long id = 2L;' },

  { code: '1.6.5', title: 'if/else/for/while 强制使用大括号', level: 'MANDATORY', category: '编程规约-控制',
    content: '在 if / else / for / while / do-while 语句中必须使用大括号，即使只有一行代码。',
    exampleBad: 'if (x > 0) return x;', exampleGood: 'if (x > 0) {\n    return x;\n}' },
  { code: '1.6.11', title: '循环体内字符串不要用 + 拼接', level: 'MANDATORY', category: '编程规约-控制',
    content: '循环体内 String 用 + 会触发 StringBuilder.append，每次循环都创建新对象，性能差。',
    exampleBad: 'String r = "";\nfor (String s : list) r += s;',
    exampleGood: 'StringBuilder sb = new StringBuilder();\nfor (String s : list) sb.append(s);' },

  { code: '2.1.1', title: 'finally 块禁止 return', level: 'MANDATORY', category: '异常日志',
    content: 'finally 块中不要用 return 语句。finally 的 return 会覆盖 try 块中的 return。',
    exampleBad: 'try { return 1; } finally { return 2; }  // 永远返回 2', exampleGood: '在 finally 里只做资源清理' },
  { code: '2.1.3', title: '不要捕获异常不处理', level: 'MANDATORY', category: '异常日志',
    content: '不能在 catch 中什么都不做（吞异常），至少要记录日志或重新抛出。',
    exampleBad: 'try { ... } catch (Exception e) { }',
    exampleGood: 'try { ... } catch (Exception e) { log.error("...", e); throw new BusinessException(...); }' },
  { code: '2.1.8', title: '禁用 e.printStackTrace()', level: 'MANDATORY', category: '异常日志',
    content: '异常不要用 System.out 或 e.printStackTrace 输出，必须使用日志框架。',
    exampleBad: 'e.printStackTrace();', exampleGood: 'log.error("操作失败, id={}", id, e);' },
  { code: '2.2.2', title: '日志参数化输出', level: 'MANDATORY', category: '异常日志',
    content: '日志使用占位符参数化，避免字符串拼接产生无用对象。',
    exampleBad: 'log.info("id=" + id + " name=" + name);',
    exampleGood: 'log.info("id={} name={}", id, name);' },

  { code: '3.6.1', title: '线程池禁用 Executors', level: 'MANDATORY', category: '并发处理',
    content: '线程池不允许使用 Executors 创建，必须通过 ThreadPoolExecutor 显式指定参数，避免默认队列无界或线程数无限增长。',
    exampleBad: 'Executors.newFixedThreadPool(10);',
    exampleGood: 'new ThreadPoolExecutor(10, 20, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000), Executors.defaultThreadFactory());' },
  { code: '3.6.3', title: '线程必须指定有意义的名称', level: 'MANDATORY', category: '并发处理',
    content: '创建线程或线程池时，请指定有意义的线程名称，便于出错时回溯。',
    exampleBad: 'new Thread(() -> ...).start();',
    exampleGood: 'new Thread(() -> ..., "order-dispatcher-1").start();' },
  { code: '3.6.7', title: 'HashMap 初始化指定容量', level: 'MANDATORY', category: '并发处理',
    content: 'HashMap 扩容有较大开销，初始化时最好预估容量（expectedSize / 0.75）。',
    exampleBad: 'Map<String, String> m = new HashMap<>();',
    exampleGood: 'Map<String, String> m = new HashMap<>(16);' },
  { code: '3.7.3', title: 'SimpleDateFormat 线程不安全', level: 'MANDATORY', category: '并发处理',
    content: 'SimpleDateFormat 是非线程安全的，多线程环境下应使用 DateTimeFormatter。',
    exampleBad: 'static SimpleDateFormat sdf = new SimpleDateFormat(...);',
    exampleGood: 'static DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");' },

  { code: '4.1.1', title: 'SQL 拼接禁止', level: 'MANDATORY', category: '安全规约',
    content: '用户输入拼接到 SQL 中存在注入风险。必须用 MyBatis #{} 或 PreparedStatement 占位符。',
    exampleBad: 'String sql = "SELECT * FROM user WHERE id=" + id;',
    exampleGood: 'SELECT * FROM user WHERE id = #{id}' },
  { code: '4.1.2', title: 'XSS 转义', level: 'MANDATORY', category: '安全规约',
    content: '输出到浏览器的用户内容必须做 HTML 转义，防止 XSS。' },
  { code: '4.1.3', title: '敏感数据脱敏', level: 'MANDATORY', category: '安全规约',
    content: '手机号、身份证、银行卡等敏感信息输出到日志/前端时必须脱敏。',
    exampleBad: 'log.info("手机号: {}", phone);',
    exampleGood: 'log.info("手机号: {}", MaskUtil.mobile(phone));' },
  { code: '4.1.6', title: '密码/密钥不得硬编码', level: 'MANDATORY', category: '安全规约',
    content: '密钥、密码、token 不得硬编码到源代码或配置文件中，应使用加密管理服务。' },

  { code: '5.1.1', title: '表必备 4 字段', level: 'MANDATORY', category: 'MySQL 数据库',
    content: '表必备三字段：id, create_time, update_time；多数业务还需 is_deleted。',
    exampleGood: 'id BIGINT PK, create_time DATETIME, update_time DATETIME, is_deleted TINYINT' },
  { code: '5.2.1', title: 'SELECT * 禁止', level: 'MANDATORY', category: 'MySQL 数据库',
    content: '禁止使用 SELECT *，必须显式列名，避免字段变更导致下游出问题。',
    exampleBad: 'SELECT * FROM user', exampleGood: 'SELECT id, name, email FROM user' },
  { code: '5.3.1', title: '索引列禁止函数运算', level: 'MANDATORY', category: 'MySQL 数据库',
    content: '索引列不要做函数运算或隐式类型转换，否则索引失效。',
    exampleBad: "WHERE DATE(create_time) = '2024-01-01'",
    exampleGood: "WHERE create_time >= '2024-01-01' AND create_time < '2024-01-02'" },
  { code: '5.2.11', title: 'JOIN 不超过 3 张表', level: 'MANDATORY', category: 'MySQL 数据库',
    content: '超过三表禁止 join。需要 join 的字段，数据类型必须绝对一致。' },

  { code: '6.1.5', title: 'Arrays.asList 返回不可变', level: 'MANDATORY', category: '集合处理',
    content: 'Arrays.asList 返回的不是 ArrayList，不能 add / remove，会抛 UnsupportedOperationException。',
    exampleBad: 'List<String> list = Arrays.asList("a","b");\nlist.add("c");',
    exampleGood: 'List<String> list = new ArrayList<>(Arrays.asList("a","b"));' },
  { code: '6.1.7', title: '集合遍历删除用 iterator.remove', level: 'MANDATORY', category: '集合处理',
    content: '不要在 foreach 循环里直接 remove/add 集合元素，会抛 ConcurrentModificationException。',
    exampleBad: 'for (String s : list) { if (cond) list.remove(s); }',
    exampleGood: 'list.removeIf(s -> cond);' },

  { code: '7.1.1', title: '分层调用约束', level: 'MANDATORY', category: '工程结构',
    content: '调用方向：Controller → Service → Manager → DAO，禁止跨层调用（如 Controller 直接调 DAO）。' },
  { code: '7.1.3', title: 'POJO 不同场景不同载体', level: 'MANDATORY', category: '工程结构',
    content: 'DO 对应数据库表、DTO 用于传输、VO 用于展现、BO 业务对象，不要混用一个类。' },

  { code: '8.2.1', title: '方法行数不超过 80', level: 'RECOMMENDED', category: '设计规约',
    content: '方法行数控制在 80 行以内，过长请拆分。' },
  { code: '8.3.1', title: '圈复杂度不超过 10', level: 'RECOMMENDED', category: '设计规约',
    content: '方法圈复杂度低于 10；超过可读性极差，应拆分。' },
]

const categoryList = computed(() => {
  const map = new Map<string, number>()
  rules.forEach(r => map.set(r.category, (map.get(r.category) || 0) + 1))
  return Array.from(map.entries()).map(([name, count]) => ({ name, count }))
})

const currentCategory = ref<string>('all')
const searchKw = ref('')
const selectedRule = ref<Rule | null>(null)

const filteredRules = computed(() => {
  let r = rules
  if (currentCategory.value !== 'all') r = r.filter(x => x.category === currentCategory.value)
  if (searchKw.value) {
    const q = searchKw.value.toLowerCase()
    r = r.filter(x => x.title.toLowerCase().includes(q) || x.code.includes(q) || x.content.toLowerCase().includes(q))
  }
  return r
})

const levelMeta = {
  MANDATORY:   { label: '强制', color: '#ef4444', bg: '#fef2f2' },
  RECOMMENDED: { label: '推荐', color: '#f59e0b', bg: '#fffbeb' },
  REFERENCE:   { label: '参考', color: '#3b82f6', bg: '#eff6ff' },
}
</script>

<template>
  <div class="rules-page">
    <aside class="side">
      <div class="side-title">📚 规约分类</div>
      <div class="cat-item" :class="{ on: currentCategory === 'all' }" @click="currentCategory = 'all'">
        全部规约 <span class="cat-count">{{ rules.length }}</span>
      </div>
      <div v-for="c in categoryList" :key="c.name"
           class="cat-item" :class="{ on: currentCategory === c.name }"
           @click="currentCategory = c.name">
        {{ c.name }} <span class="cat-count">{{ c.count }}</span>
      </div>
    </aside>

    <main class="main">
      <div class="search-bar">
        <el-input v-model="searchKw" placeholder="搜索条款编号 / 标题 / 关键字" clearable style="width: 400px" />
        <span class="total">共 {{ filteredRules.length }} 条</span>
      </div>

      <div class="rules-grid">
        <div v-for="r in filteredRules" :key="r.code" class="rule-card"
             :class="{ active: selectedRule?.code === r.code }"
             @click="selectedRule = r">
          <div class="rule-top">
            <span class="rule-code">{{ r.code }}</span>
            <span class="rule-level"
                  :style="{ background: levelMeta[r.level].bg, color: levelMeta[r.level].color }">
              {{ levelMeta[r.level].label }}
            </span>
          </div>
          <div class="rule-title">{{ r.title }}</div>
          <div class="rule-cat">{{ r.category }}</div>
        </div>
      </div>
    </main>

    <!-- 详情抽屉 -->
    <el-drawer v-model="selectedRule" :with-header="false" size="580px" v-if="selectedRule">
      <div class="detail" v-if="selectedRule">
        <div class="d-top">
          <span class="rule-code">{{ selectedRule.code }}</span>
          <span class="rule-level"
                :style="{ background: levelMeta[selectedRule.level].bg, color: levelMeta[selectedRule.level].color }">
            【{{ levelMeta[selectedRule.level].label }}】
          </span>
        </div>
        <h2 class="d-title">{{ selectedRule.title }}</h2>
        <div class="d-cat">{{ selectedRule.category }}</div>
        <div class="d-section">
          <div class="d-h">📝 说明</div>
          <div class="d-body">{{ selectedRule.content }}</div>
        </div>
        <div v-if="selectedRule.exampleBad" class="d-section">
          <div class="d-h d-h-bad">❌ 反例</div>
          <pre class="d-code d-code-bad">{{ selectedRule.exampleBad }}</pre>
        </div>
        <div v-if="selectedRule.exampleGood" class="d-section">
          <div class="d-h d-h-good">✅ 正例</div>
          <pre class="d-code d-code-good">{{ selectedRule.exampleGood }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.rules-page { display: grid; grid-template-columns: 220px 1fr; gap: 16px; padding: 20px; height: 100%; }
.side { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); overflow-y: auto; }
.side-title { font-size: 14px; font-weight: 600; margin-bottom: 10px; color: #111827; }
.cat-item {
  padding: 8px 12px; border-radius: 6px; cursor: pointer;
  display: flex; justify-content: space-between; align-items: center;
  color: #374151; font-size: 13px; transition: background 0.2s;
}
.cat-item:hover { background: #f3f4f6; }
.cat-item.on { background: #ede9fe; color: #6d28d9; font-weight: 500; }
.cat-count { font-size: 11px; color: #9ca3af; }
.cat-item.on .cat-count { color: #6d28d9; }

.main { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); overflow-y: auto; }
.search-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.total { color: #6b7280; font-size: 13px; }
.rules-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.rule-card {
  padding: 14px; border: 1px solid #e5e7eb; border-radius: 8px;
  cursor: pointer; transition: all 0.2s;
}
.rule-card:hover { border-color: #6d28d9; transform: translateY(-2px); box-shadow: 0 4px 8px rgba(109, 40, 217, 0.1); }
.rule-card.active { border-color: #6d28d9; background: #faf5ff; }
.rule-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rule-code { font-family: monospace; font-size: 11px; color: #6d28d9; font-weight: 600; }
.rule-level { padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; }
.rule-title { font-size: 14px; color: #111827; font-weight: 500; margin-bottom: 4px; line-height: 1.5; }
.rule-cat { font-size: 11px; color: #9ca3af; }

.detail { padding: 10px; }
.d-top { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.d-title { font-size: 20px; font-weight: 600; margin: 0 0 6px; color: #111827; }
.d-cat { color: #6b7280; font-size: 13px; margin-bottom: 20px; }
.d-section { margin-bottom: 18px; }
.d-h { font-weight: 600; margin-bottom: 8px; color: #374151; }
.d-h-good { color: #059669; }
.d-h-bad { color: #dc2626; }
.d-body { color: #4b5563; line-height: 1.7; }
.d-code { padding: 12px; border-radius: 6px; font-size: 13px; font-family: 'SF Mono', monospace; overflow-x: auto; line-height: 1.6; }
.d-code-bad { background: #fef2f2; color: #7f1d1d; border-left: 3px solid #ef4444; }
.d-code-good { background: #f0fdf4; color: #14532d; border-left: 3px solid #10b981; }
</style>
