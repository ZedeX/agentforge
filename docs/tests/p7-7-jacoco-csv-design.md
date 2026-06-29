# P7-7 JaCoCo CSV 配置优化设计文档

> 任务编号：P7-7（Info 级，v7 §4.3 后续改进）
> 创建日期：2026-06-29
> 子 Agent：P7-7
> 关联文档：[tdd-audit-report-v7.md](tdd-audit-report-v7.md) §4.3

---

## 1. 现状分析

### 1.1 任务来源

TDD 审计 v7.2 报告 §4.3 指出：

> 各模块 `jacoco.csv` 仅导出**依赖 bundle**（GROUP 字段含 `/`，如 `agent-session/agent-common`、`agent-task-orchestrator/agent-proto`），**不导出模块自有 bundle**（GROUP 字段无 `/`，如 `agent-session`、`agent-task-orchestrator`）。

并建议（Info 级，不影响评分）：

> v8 复核可考虑调整 JaCoCo `report` goal 配置，使 CSV 包含模块自有 bundle …，便于后续审核员精确校验各模块自有类覆盖率。

### 1.2 当前 JaCoCo 配置摘要（根 pom.xml）

`e:\git\Agent-Platform-Prototype\pom.xml` 第 242-302 行声明 `jacoco-maven-plugin`，关键配置如下：

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco-maven-plugin.version}</version>   <!-- 0.8.11 -->
    <configuration>
        <outputDirectory>${project.build.directory}/site/jacoco</outputDirectory>
        <!-- Exclude generated code: protobuf-generated classes, gRPC stubs, Lombok-generated -->
        <excludes>
            <exclude>**/com/google/protobuf/**</exclude>
            <exclude>**/*OuterClass*</exclude>
            <exclude>**/*Grpc*</exclude>
            <exclude>**/generated-sources/**</exclude>
            <exclude>**/io/grpc/**</exclude>
        </excludes>
    </configuration>
    <executions>
        <execution>
            <id>jacoco-prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>jacoco-report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <haltOnFailure>true</haltOnFailure>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>${jacoco.line.coverage}</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>${jacoco.branch.coverage}</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**关键观察**：
- `<configuration>` 是 plugin 全局配置，对 **所有 goal**（prepare-agent / report / check / report-aggregate）共享生效
- `<outputDirectory>` 为 `${project.build.directory}/site/jacoco`，被 `report` goal 和命令行调用的 `report-aggregate` goal 共用
- 没有显式 `<includes>` 声明（默认包含所有类）
- `<executions>` 中没有声明 `report-aggregate`，说明该 goal 仅由 CI 命令行触发，不绑定到任何 phase

### 1.3 CI workflow 中的 JaCoCo 使用

`e:\git\Agent-Platform-Prototype\.github\workflows\ci.yml` 第 57-59 行：

```yaml
- name: Aggregate JaCoCo report
  if: always()
  run: mvn -B -ntp jacoco:report-aggregate || true
```

**关键观察**：
- 命令 `mvn jacoco:report-aggregate` 在 **reactor 模式** 下运行，会在每个 reactor 项目（根 pom + 5 个子模块 + 当前未列出的临时模块）上执行 `report-aggregate` goal
- 末尾 `|| true` 表示该步骤失败不影响 CI 通过（说明该步骤是"锦上添花"，非必需）

### 1.4 问题现象（v7 §4.3 描述）

基于 CI Run 28374714467 artifact 下载至 `tmp/ci-jacoco-v6/` 的 CSV 检查：

| 模块 CSV | GROUP 字段格式 | 包含的类 | 缺失的类 |
|---|---|---|---|
| `agent-common/jacoco.csv` | `agent-common/agent-proto` | 218 条 proto 类 | **com.agent.common.* 9 类业务代码缺失** |
| `agent-session/jacoco.csv` | `agent-session/agent-common` | 9 条 com.agent.common.* | **com.agent.session.* 14 类缺失** |
| `agent-task-orchestrator/jacoco.csv` | `agent-task-orchestrator/agent-proto` + `agent-task-orchestrator/agent-common` | 218 proto + 9 common | **com.agent.orchestrator.* 18 类缺失** |
| `agent-gateway/jacoco.csv` | `agent-gateway/agent-proto` | 218 条 proto 类 | **com.agent.gateway.* 24 类缺失** |

GROUP 字段含 `/` 是 **`report-aggregate` goal 输出的格式**（bundle 名为 `groupId/artifactId`），而 `report` goal 输出的 GROUP 字段为 `agent-X`（bundle 名为模块名）。

### 1.5 main 分支当前 CSV 状态（与 v7 描述不符）

直接检查 `e:\git\Agent-Platform-Prototype\agent-*/target/site/jacoco/jacoco.csv`（main 分支本地 `mvn test` 生成）：

| 模块 CSV | GROUP 字段格式 | 包含的类 |
|---|---|---|
| `agent-common/jacoco.csv` | `agent-common` | 9 条 com.agent.common.* + 0 条 proto 类 |
| `agent-session/jacoco.csv` | `agent-session` | 14 条 com.agent.session.* |
| `agent-task-orchestrator/jacoco.csv` | `agent-task-orchestrator` | 32 条 com.agent.orchestrator.* |
| `agent-gateway/jacoco.csv` | `agent-gateway` | 24 条 com.agent.gateway.* |

**main 分支本地 CSV 是正确的**（包含模块自有 bundle），与 v7 §4.3 描述的 CI artifact CSV 不一致。这表明问题只在 CI 环境中出现，本地构建不会复现。

---

## 2. 根因分析

### 2.1 假设

**任务原假设**：`report` goal 配置缺少 `includes`，导致 CSV 缺失模块自有 bundle。

**实际根因**（经临时分支验证）：CI workflow 中的 `mvn jacoco:report-aggregate` 命令在 reactor 模式下运行，会在**每个子模块**上执行 `report-aggregate` goal，由于：

1. `report-aggregate` goal 的语义是**聚合 dependencies 的覆盖率**，输出只包含 dependencies 的类，**不包含模块自身的类**
2. plugin 全局 `<configuration>` 中 `<outputDirectory>` 为 `${project.build.directory}/site/jacoco`，与 `report` goal 共用，导致 `report-aggregate` 输出**覆盖**了 `report` 生成的 CSV
3. `report-aggregate` 在子模块上运行时，会加载该模块 dependencies 的 `jacoco.exec`（如 agent-common 加载 agent-proto 的 exec），生成 GROUP=`agent-X/agent-Y` 格式的 CSV

### 2.2 验证证据（临时分支 tmp/p7-7-jacoco-test，已删除）

在临时分支上执行三步验证：

**步骤 1**：`mvn -B -ntp -pl agent-common -am test`（仅运行 test phase，触发 `jacoco:report`）

```bash
$ head -3 agent-common/target/site/jacoco/jacoco.csv
GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,...
agent-common,com.agent.common.constant,AgentStatus,0,70,0,4,0,13,0,6,0,4
agent-common,com.agent.common.constant,ComplexityLevel,0,99,0,4,0,20,0,10,0,8
```

→ CSV 正确，GROUP=`agent-common`，包含模块自有类。

**步骤 2**：`mvn -B -ntp -pl agent-common jacoco:report-aggregate`（命令行调用 report-aggregate，模拟 CI 行为）

```bash
$ head -3 agent-common/target/site/jacoco/jacoco.csv
GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,...
(空，只有 header)
```

→ CSV 被覆盖为空（因 -pl agent-common 未带 -am，无 agent-proto 的 jacoco.exec 可加载）。

**步骤 3**：`mvn -B -ntp jacoco:report-aggregate`（全 reactor 模式，模拟 CI 真实行为）

```bash
$ head -3 agent-common/target/site/jacoco/jacoco.csv
GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,...
agent-common/agent-proto,agentplatform.agent_runtime.v1,PauseResponse.Builder,497,0,47,0,...

$ head -3 agent-session/target/site/jacoco/jacoco.csv
GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,...
agent-session/agent-common,com.agent.common.constant,AgentStatus,0,70,0,4,0,13,0,6,0,4

$ head -3 agent-task-orchestrator/target/site/jacoco/jacoco.csv
GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,...
agent-task-orchestrator/agent-proto,agentplatform.agent_runtime.v1,PauseResponse.Builder,497,0,47,0,...
```

→ CSV 完全复现 v7 §4.3 描述的现象：GROUP=`agent-X/agent-Y` 格式，只含依赖 bundle，模块自有类消失。

**结论**：根因 100% 确认为 CI 中 `mvn jacoco:report-aggregate` 在子模块上覆盖了 `report` 生成的 CSV。

### 2.3 为什么 main 分支本地 CSV 正确？

main 分支本地 CSV 是 `mvn test` 生成的，没有运行 `report-aggregate`，所以保留了 `report` goal 的正确输出。CI 中多了一步 `mvn jacoco:report-aggregate`，导致 CSV 被覆盖。

### 2.4 为什么 `|| true`？

CI 中 `|| true` 表示 report-aggregate 失败不影响 CI。原因：
1. 根 pom 不是 aggregator 结构（用 `<modules>` 而非 `<dependencies>` 声明子模块），report-aggregate 在根 pom 上运行会生成空报告
2. report-aggregate 在 packaging=jar 的子模块上运行虽能输出 CSV，但语义错误（应是聚合，不是覆盖子模块报告）

该步骤本意是生成聚合报告，但因根 pom 结构不支持，实际只产生副作用（覆盖子模块 CSV），无正向价值。

---

## 3. 优化方案

### 3.1 推荐方案：CI workflow 添加 `-N` 限制 report-aggregate 范围

**修改 `.github/workflows/ci.yml` 第 57-59 行**：

```diff
       - name: Aggregate JaCoCo report
         if: always()
-        run: mvn -B -ntp jacoco:report-aggregate || true
+        # P7-7 整改 (ref tdd-audit-report-v7.md §4.3):
+        # -N (--non-recursive) 限制 report-aggregate 只在根 pom 运行,
+        # 避免在子模块上覆盖 jacoco:report 生成的 CSV (根因: report-aggregate
+        # 在 jar 模块上运行时只输出 dependencies 的类, 覆盖了模块自有 bundle).
+        # 根 pom 当前不是 aggregator 结构 (用 <modules> 而非 <dependencies>),
+        # report-aggregate 在根 pom 上会生成空报告, 但保留该步骤以备后续
+        # 重构为 aggregator pom 时启用.
+        run: mvn -B -ntp -N jacoco:report-aggregate || true
```

**原理**：
- `-N` (= `--non-recursive`) 限制 Maven 只在当前项目（根 pom）运行，不进入子模块
- report-aggregate 只在根 pom 上运行，不会覆盖子模块的 `target/site/jacoco/jacoco.csv`
- 子模块的 CSV 保持 `jacoco:report` 生成的正确内容（包含模块自有 bundle）

**影响评估**：
- ✅ **不破坏现有 CI 通过**：原命令有 `|| true`，本就容错；新命令在根 pom 上运行 report-aggregate 会生成空报告（根 pom 无 dependencies 指向子模块），但 `|| true` 兜底
- ✅ **不影响覆盖率阈值**：`jacoco:check` 在 `verify` phase 运行（不受 report-aggregate 影响），仍按 BUNDLE 规则校验模块自有 bundle
- ✅ **修复 v7 §4.3 问题**：CI artifact 中的子模块 CSV 将包含模块自有 bundle，便于后续审核员精确校验
- ⚠️ **丢失聚合报告功能**：根 pom 当前结构不支持真正的聚合报告（需 aggregator pom）。该功能本就不可用，此方案只是显式化这一事实。后续如需聚合报告，应创建独立的 aggregator pom（如 `agent-coverage-aggregator`），声明所有子模块作为 dependencies

### 3.2 备选方案对比

| 方案 | 描述 | 优点 | 缺点 | 推荐度 |
|---|---|---|---|---|
| **A. CI 加 -N**（推荐） | `mvn -N jacoco:report-aggregate` | 最小改动，根因修复，不破坏 CI | 聚合报告仍空（根 pom 非 aggregator 结构） | ⭐⭐⭐⭐⭐ |
| B. CI 删除该步骤 | 直接移除 report-aggregate 步骤 | 最简单 | 丢失聚合意图，未来启用需重新添加 | ⭐⭐⭐ |
| C. pom.xml 加 includes | `<includes><include>com/agent/**</include></includes>` | CSV 更干净（过滤 proto 类） | 非根因修复；会改变现有 CSV 行为（丢失 proto 类数据，可能影响其他审核） | ⭐⭐ |
| D. pom.xml 用 property 解耦 outputDirectory | `<jacoco.outputDirectory>` property + CI 传 -D | 灵活，可分别配置 | 实现复杂，-D 参数在 reactor 模式下传递困难 | ⭐⭐ |
| E. 创建独立 aggregator pom | 新增 `agent-coverage-aggregator` 模块 | 真正支持聚合报告 | 改动大，超出 P7-7 范围 | ⭐（v8 可考虑） |

### 3.3 关于 pom.xml 是否需要修改

**结论：pom.xml 不需要修改**。

- 根因不在 pom.xml 的 `report` goal 配置，而在 CI workflow 的 `report-aggregate` 命令行调用
- 当前 pom.xml 的 `<excludes>` 配置合理（排除 protobuf/gRPC 生成代码）
- 添加 `<includes>` 会改变现有 CSV 行为（过滤掉 proto 类），可能影响 v7 §3.1 中"业务代码 9 类 vs proto 类稀释"的对比基线
- 最小改动原则：只修根因，不做预防性变更

如果主 Agent 认为需要让 CSV 更干净（只含 com.agent.* 业务代码），可考虑备选方案 C，但建议在 v8 复核时单独评估，不混入 P7-7。

---

## 4. 验证计划

### 4.1 验证目标

确认优化后的 CI 产出的 `agent-*/target/site/jacoco/jacoco.csv` 包含模块自有 bundle（GROUP 字段为 `agent-X`，无 `/`）。

### 4.2 验证步骤

**前置条件**：
- 已应用 §3.1 的 CI workflow patch
- 触发 CI Run（push 到 feature 分支或 PR）

**步骤 1**：等待 CI Run 完成，下载 `jacoco-coverage-report` artifact

```bash
gh run download <RUN_ID> -n jacoco-coverage-report -D tmp/ci-jacoco-p7-7/
```

**步骤 2**：检查各模块 CSV 的 GROUP 字段

```bash
# 期望: GROUP 字段为 "agent-common" (无 /), 包含 com.agent.common.* 9 类
head -3 tmp/ci-jacoco-p7-7/agent-common/target/site/jacoco/jacoco.csv

# 期望: GROUP 字段为 "agent-session", 包含 com.agent.session.* 14 类
head -3 tmp/ci-jacoco-p7-7/agent-session/target/site/jacoco/jacoco.csv

# 期望: GROUP 字段为 "agent-task-orchestrator", 包含 com.agent.orchestrator.* 32 类
head -3 tmp/ci-jacoco-p7-7/agent-task-orchestrator/target/site/jacoco/jacoco.csv

# 期望: GROUP 字段为 "agent-gateway", 包含 com.agent.gateway.* 24 类
head -3 tmp/ci-jacoco-p7-7/agent-gateway/target/site/jacoco/jacoco.csv
```

**步骤 3**：统计各模块 CSV 行数（应为模块自有类数 + 1 header）

```bash
wc -l tmp/ci-jacoco-p7-7/agent-*/target/site/jacoco/jacoco.csv
```

**预期输出**（参考 main 分支本地 mvn test 结果）：

| 模块 | 预期行数（含 header） | 预期 GROUP |
|---|---|---|
| agent-common | 10 (1 + 9 类) | `agent-common` |
| agent-session | 15 (1 + 14 类) | `agent-session` |
| agent-task-orchestrator | 33 (1 + 32 类) | `agent-task-orchestrator` |
| agent-gateway | 25 (1 + 24 类) | `agent-gateway` |

**步骤 4**：交叉验证 — 抽取 com.agent.orchestrator.* / com.agent.session.* / com.agent.gateway.* 各 1 个类，核对覆盖率数据与 HTML 报告一致

```bash
# 例如核对 TaskStateMachine
grep "TaskStateMachine" tmp/ci-jacoco-p7-7/agent-task-orchestrator/target/site/jacoco/jacoco.csv
# 与 HTML 报告 com.agent.orchestrator.statemachine/TaskStateMachine.html 的数据比对
```

### 4.3 回归验证

- ✅ CI 应仍通过（`mvn verify` 的 `jacoco:check` 不受影响）
- ✅ 各模块覆盖率阈值（line ≥0.80 / branch ≥0.70）应仍达标（CSV 数据未变，只是导出范围修正）
- ✅ PR 评论中的覆盖率摘要（madrapps/jacoco-report）应仍正常工作（读 jacoco.xml，不受 CSV 影响）

---

## 5. patch 文本

### 5.1 主 patch：CI workflow 修复（根因修复，必须）

> 已通过 `git apply --check` 验证可正确应用（验证于临时分支 tmp/p7-7-patch-test）。

```diff
diff --git a/.github/workflows/ci.yml b/.github/workflows/ci.yml
--- a/.github/workflows/ci.yml
+++ b/.github/workflows/ci.yml
@@ -54,9 +54,13 @@ jobs:
       - name: Run integration tests + JaCoCo coverage check
         run: mvn -B -ntp verify

-      - name: Aggregate JaCoCo report
+      - name: Aggregate JaCoCo report (non-recursive, root pom only)
         if: always()
-        run: mvn -B -ntp jacoco:report-aggregate || true
+        # P7-7 整改 (ref tdd-audit-report-v7.md §4.3):
+        # -N (--non-recursive) 限制 report-aggregate 只在根 pom 运行,
+        # 避免在子模块上覆盖 jacoco:report 生成的 CSV (report-aggregate
+        # 在 jar 模块上运行时只输出 dependencies 的类, 覆盖了模块自有 bundle).
+        run: mvn -B -ntp -N jacoco:report-aggregate || true

       - name: Upload JaCoCo coverage report
         if: always()
```

**应用方式**（任选其一）：

```bash
# 方式 A: 保存 patch 文本为 p7-7-ci.patch, 然后 git apply
cd e:\git\Agent-Platform-Prototype
git apply p7-7-ci.patch

# 方式 B: 直接 git checkout -b 分支后手动 Edit, 然后 git diff 确认
```

### 5.2 可选 patch：pom.xml 预防性 includes（不推荐，备选）

> ⚠️ 此 patch 会改变现有 CSV 行为（过滤掉 proto 类），可能影响 v7 §3.1 的对比基线。仅在不满意当前 CSV 含 proto 类稀释时考虑应用。

```diff
--- a/pom.xml
+++ b/pom.xml
@@ -246,6 +246,12 @@
                     <configuration>
                         <outputDirectory>${project.build.directory}/site/jacoco</outputDirectory>
+                        <!-- P7-7 可选整改: 显式声明 includes 限定 report 范围为业务代码 (com.agent.*),
+                             过滤掉 proto 生成类等依赖代码, 使 CSV 更干净便于审核.
+                             注: includes 仅影响 jacoco:report 输出, 不影响 jacoco:check 阈值校验.
+                             不应用此 patch 也可 (proto 类已被 excludes 部分过滤, 且不影响业务类覆盖率校验). -->
+                        <includes>
+                            <include>com/agent/**</include>
+                        </includes>
                         <!-- Exclude generated code: protobuf-generated classes, gRPC stubs, Lombok-generated -->
                         <excludes>
                             <exclude>**/com/google/protobuf/**</exclude>
```

---

## 6. 实施清单

- [ ] 主 Agent 应用 §5.1 主 patch（CI workflow）
- [ ] 主 Agent commit 并 push 触发 CI
- [ ] 等待 CI Run 完成，按 §4.2 步骤验证 CSV
- [ ] 验证通过后，更新 project_memory.md 记录 P7-7 完成
- [ ] v8 复核时确认 CSV 包含模块自有 bundle（解除 v7 §4.3 的 Info 级观察）

## 7. 风险与决策事项

### 7.1 风险

- **低风险**：CI workflow 修改仅影响 report-aggregate 步骤，该步骤有 `|| true` 兜底，不会破坏 CI
- **无风险**：pom.xml 不修改，不影响构建行为

### 7.2 需主 Agent 决策事项

1. **是否应用可选 patch（§5.2）**：当前推荐不应用（保留 proto 类在 CSV 中，便于完整审计）。如主 Agent 倾向"CSV 只含业务代码"，可应用 §5.2，但需同步更新 v7 §3.1 的对比基线说明
2. **是否在 v8 创建独立 aggregator pom（方案 E）**：超出 P7-7 范围，建议作为 v8 改进项单独评估
3. **commit message 建议**：`ci(jacoco): fix report-aggregate overwriting module CSV (P7-7)`

---

## 8. 参考

- [tdd-audit-report-v7.md](tdd-audit-report-v7.md) §4.3 JaCoCo 报告配置观察
- [JaCoCo Maven Plugin 文档](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
  - `report` goal：生成单模块覆盖率报告（CSV/HTML/XML）
  - `report-aggregate` goal：聚合 dependencies 的覆盖率报告（设计给 aggregator pom 用）
  - `<outputDirectory>` 配置对所有 goal 共享
- 临时分支验证记录（tmp/p7-7-jacoco-test，已删除）：
  - `mvn test` → CSV GROUP=`agent-X`，含模块自有类 ✓
  - `mvn jacoco:report-aggregate` → CSV GROUP=`agent-X/agent-Y`，模块自有类消失 ✗（复现 v7 §4.3）
