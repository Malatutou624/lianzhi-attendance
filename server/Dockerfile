# =====================================================================
# 多阶段构建：Maven 编译 → JRE 运行
# =====================================================================

# ---- 阶段 1: Maven 编译 ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# 先拷 pom.xml 预热依赖（利用 Docker 缓存，pom 不变就不重新下载）
COPY pom.xml .
RUN mvn dependency:go-offline -B -q || true

# 拷源码 + 本地 ArcFace jar（systemPath 依赖）
COPY src ./src
COPY libs ./libs

# 跳过测试打包（CI 阶段已跑过测试）
RUN mvn clean package -DskipTests -B -q && ls -lh target/*.jar

# ---- 阶段 2: JRE 运行 ----
FROM eclipse-temurin:17-jre

WORKDIR /app

# 拷编译产物（Spring Boot fat jar）
COPY --from=build /app/target/*.jar app.jar

# 拷 ArcFace DLL 目录（Linux 下加载会失败 → 自动 fallback pHash，应用不崩）
COPY arcsoft ./arcsoft

# 创建 uploads / knowledge 目录（后续用 volume 挂载持久化）
RUN mkdir -p /app/uploads /app/knowledge

# 时区 + 编码（强制 UTF-8，防止 Linux 容器用 POSIX/C locale 导致 JVM 默认编码出问题）
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

EXPOSE 8080

# JVM 参数：强制 UTF-8 + 容器堆给 512MB 足够
# -Dfile.encoding=UTF-8       强制 JVM 默认编码（Linux 容器 C locale 默认会是 US-ASCII 或 ANSI_X3.4-1968）
# -Dsun.stdout.encoding=UTF-8 保证 stdout/stderr 也用 UTF-8（日志里中文不乱码）
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-jar", "app.jar"]
