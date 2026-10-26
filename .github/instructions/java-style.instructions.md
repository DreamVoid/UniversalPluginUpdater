---
description: "Use when writing or editing any Java source in this project. Covers Javadoc conventions (class/record/constructor/method), comment style, nullability annotations, and formatting. Trigger words: Javadoc, 注释, comment, doc comment, 文档注释."
applyTo: "**/*.java"
---
# Java 注释与 Javadoc 风格

本项目源码目标为 Java 17，所有注释、Javadoc 一律使用简体中文；标识符、API 名与术语保持原文（如 `UpdateManager`、`@Nullable`、`Modrinth`）。
风格以当前代码现状为准：Javadoc 精简，只保留摘要与标签；长设计理由放 `AGENTS.md` 或就地 `//` 注释，不堆进类级 Javadoc。

## 1. 谁需要写 Javadoc

- `public` / `protected` 类型、record、构造器、非 `@Override` 方法必须写 Javadoc。
- `@Override` 方法继承父类或接口文档，默认不重复；实现存在额外约束时用 `//` 补充。
- 类级 Javadoc 只写一句短语摘要；需要时用 `<br>` 补充一行领域约束或使用说明。
- 私有成员默认不写 Javadoc；需要说明的私有字段使用 `//`。本项目是 Java 17，不使用 Java 23+ 的 `///` 文档注释。

## 2. 摘要与标签

- 摘要为简短中文短语，句末不加句号；补充说明用 `<br>` 折行。
- `@param` / `@return` / `@throws` 描述简短；多参数时按列对齐。
- 声明了受检异常的方法用 `@throws` 说明抛出条件。
- 引用类型或方法用 `{@link X}`，引用字面量、`null`、标识符用 `{@code x}`；关联类型或重载可用 `@see`。

```java
/**
 * 使用指定游戏版本检查所有插件的更新<br>
 * 覆盖值仅对本次检查生效
 *
 * @param gameVersions 目标游戏版本列表，为 {@code null} 或空时使用配置/平台检测值
 * @return 待更新的插件列表
 */
```

## 3. record 与构造器

- record 用 `@param` 逐一列出组件。
- 实体类构造器逐参数写 `@param`，可空参数同样标注。
- 与配置、存储或注册表关联时，用 `@see` 指向对应类型。

## 4. 行内注释

- 只解释「为什么」，不复述代码已表达的「做什么」。
- 时序坑点、方言差异、取舍理由用连续 `//` 说明，能一句话讲清就不铺开。
- 分节或长方法步骤可用短语注释，如 `// 构建查询参数`、`// 恢复 ThreadLocal`。

```java
// 目标版本变化会改变请求 URL，旧令牌与旧选中版本不可复用。
```

## 5. 可空性与格式

- 可空性逐成员显式标注 `@Nullable` / `@NotNull`（`org.jetbrains.annotations`），项目没有 `@NullMarked`。
- 注解与被注解成员分行；字段、方法各自另起一行。
- K&R 大括号（左括号不换行），4 空格缩进。
- 注解较密集的包使用通配 import，例如 `import java.util.*;`。
