# ADR-0002: 服务器用 Java 实现报告单解密(不 shell-out npm 包)

## Context

Autograder 报告单是 AES-256-GCM 加密文件。issue #1 的原案是"服务器用同一个 npm 包的 decrypt 子命令"以保证两端算法永不漂移,但官网后端是 Spring Boot / Java 17 部署机,引入 Node 运行时 + npm 依赖会多一条部署链。我们选择在 Java 侧用 JDK 原生 crypto 重实现 AES-256-GCM 解密。

加密密钥随工具仓公开分发(荣誉系统),所以密钥本身没有保密负担;真正的风险是**密文信封格式漂移**。对策不是"共用实现",而是把格式固化为跨仓库契约:

1. 工具仓(`Boyuan-Autograder/interview-autograder`)发布一份**密文格式规范**:IV 长度与位置、authTag 位置、载荷编码(base64/hex)、有无 JSON 外壳、AAD 使用情况。
2. 工具仓提供**固定测试向量**(已知明文 → 已知密文),Java 侧解密实现必须通过向量验证。
3. 明文报告单的 JSON schema(plaintext)同样作为契约,Java 侧建 DTO 对齐。

## Status

accepted

## Considered Options

- **Shell-out npm 包**(issue #1 原案):服务器需装 Node + github: 依赖,部署链多一环;收益是"实现单一来源"。被否:与现有纯 JVM 部署模型冲突,且密文格式只要冻结,Java 用标准算法实现就不存在"不知道对方怎么加密"的问题。
- **无格式契约的 Java 重实现**:被否,必然漂移。

## Consequences

- 后端无新运行时依赖;intake 端点纯 JVM。
- 跨仓库契约(格式文档 + 测试向量 + 明文 JSON schema)必须由工具仓团队交付,官网开发被该交付阻塞——它是本 ADR 的前置依赖。
- 报告格式升级 = 工具仓发新版规范 + 向量,Java 侧同步;风险收敛到"契约文档更新",而非"两套代码对不上"。