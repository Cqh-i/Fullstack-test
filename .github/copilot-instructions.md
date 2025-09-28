# UI 指南（本项目）
- 所有交互与组件一律使用 **Web Awesome** 的 Web Components（`<wa-*>`）。如按钮用 `<wa-button>`, 对话框用 `<wa-dialog>`, 表单控件用 `<wa-input>` 等；避免生成原生 `<button>`、自建弹窗或其它库组件。
- 代码示例中必须包含组件的关键属性与事件（如 `variant="brand"`、`slot="footer"`、`data-dialog="close"` 等），并遵循官方 API。
- 如需等待组件注册，请使用 `customElements.whenDefined('wa-xxx')` 或 `allDefined()`（存在多组件时）。

# 设计令牌（Design Tokens）
- **颜色**：禁止硬编码十六进制 / rgb / hsl；统一使用 `var(--wa-color-{group}-{tint})`（如 `--wa-color-brand-60`, `--wa-color-neutral-95`）。
- **间距**：禁止固定 `px`；只允许 `var(--wa-space-{scale})`，其中 `{scale}` ∈ `3xs, 2xs, xs, s, m, l, xl, 2xl, 3xl, 4xl`。
    - **禁止**使用数字后缀（如 `--wa-space-6/5/4`）。
    - 全局缩放用 `--wa-space-scale`（默认 1）。
- **圆角/阴影/描边**：统一用 `--wa-border-radius-*` 与 `--wa-shadow-*`；不要自定义阴影颜色。
  > 若你的版本没有 `--wa-color-shadow`，不要自行添加，直接使用 `--wa-shadow-*` 即可。
- **排版/链接**：使用 `--wa-font-size-*`、`--wa-link-*` 等排版令牌；避免手写像素字号。


## Web Awesome 设计令牌约束（必须遵守）
- 间距只允许使用字母刻度：`3xs, 2xs, xs, s, m, l, xl, 2xl, 3xl, 4xl`。
- **禁止**使用数字后缀（如 `--wa-space-6/5/4` 等）。
- 只能这样写：`gap: var(--wa-space-m)` / `padding: var(--wa-space-l)`。
- 颜色等同理：只用 `--wa-color-{group}-{tint}`；禁止硬编码十六进制。
- 若不确定令牌是否存在：先用 `getComputedStyle(...).getPropertyValue('--token')` 验证。
### 示例
- ✅ `padding: var(--wa-space-l)`
- ❌ `padding: var(--wa-space-6)`

# 实用类与变体
- 优先使用 Web Awesome 提供的实用类和变体：
    - 颜色变体：`.wa-brand/.wa-neutral/.wa-success/.wa-warning/.wa-danger` 或组件的 `variant="brand|neutral|success|warning|danger"`
    - 布局与栈间距：`wa-stack`（表单/竖排布局）
    - 圆角工具：`wa-border-radius-s|m|l|pill|circle|square`

# 技术栈约束（必须遵守）

- 后端：Spring Boot + Kotlin

    - 事务：优先 TransactionTemplate 编程式事务。

    - 数据访问：优先 JdbcClient；禁止拼接 SQL。

    - 控制器返回 Thymeleaf 片段供 HTMX 局部更新；必要时设置 HX 相关响应头（如 HX-Retarget, HX-Reswap, HX-Trigger）。

    - 校验：@Valid + Bean Validation；校验失败返回表单片段，而非整页。

- 数据库：Postgres

  - 字段类型：优先 uuid / bigint 主键、timestamptz 时间、可变结构使用 jsonb。

  - Upsert：使用 INSERT ... ON CONFLICT (...) DO UPDATE。

  - 只写占位参数（? 或命名参数），不要内联变量。

  - 索引：对高频查询条件建立索引（如唯一键、外键、时间 + 状态组合等）。

# 前端渲染：HTMX + Thymeleaf SSR
- 用 hx-get|hx-post|hx-patch|hx-delete 发起请求；用 hx-target 和 hx-swap 精准替换 DOM 区域。

- 控制器分别返回：成功片段（如表格 <tbody>）与失败片段（表单/错误提示）。

- 表单提交前端校验可选；后端为准，错误时仅更新表单片段。

## Thymeleaf 表达式规范（必须遵守）
- 禁止在同一表达式中嵌套第二层占位（即 ${...${...}...}）。
- 需要条件或格式化时，只写单层 SpringEL；复杂值一律在控制器预计算后注入 Model。

示例：
- 正确：`th:attr="value=${tagsJoined ?: ''}"`
- 正确：`th:text="${#strings.listJoin(tags, ', ')}"`
- 错误：`th:attr="value=${product.tags != null ? ${#strings.listJoin(product.tags, ', ')} : ''}"`

建议：
- 涉及 join、format、三元运算的表达式超过一屏时，改为在控制器预计算（如 tagsJoined）。
