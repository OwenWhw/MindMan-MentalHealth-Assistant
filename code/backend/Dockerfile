# ============================================================
# MindMan 后端镜像 —— 多阶段构建
#   阶段 1：用 Maven 编译打包（构建工具不进入最终镜像）
#   阶段 2：只带 JRE 运行，镜像体积更小
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 使用阿里云 Maven 镜像源，否则容器内拉依赖会非常慢
COPY settings.xml /root/.m2/settings.xml

# 先只复制 pom.xml 单独下载依赖：只要 pom 没改，这一层就命中缓存，
# 之后改代码重新构建时不必再拉一遍依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再复制源码编译打包
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区设为上海，否则容器内日志时间会差 8 小时
ENV TZ=Asia/Shanghai

COPY --from=build /build/target/mindman-server-1.0.0.jar app.jar

EXPOSE 8080

# 用 exec 形式（JSON 数组）启动，java 才是 1 号进程，
# docker stop 时的 SIGTERM 才能被 JVM 收到，实现优雅停机
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
