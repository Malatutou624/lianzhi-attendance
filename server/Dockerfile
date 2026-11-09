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

# 时区 + 编码
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

EXPOSE 8080

# JVM 参数：容器里堆给 512MB 足够（2核2G 默认配置）
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
