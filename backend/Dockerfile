# MVP-0 后端镜像（多阶段构建）
# 阶段1：用 Maven 在 JDK21 下编译打包
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# 先拉取依赖（利用层缓存；失败不阻断，下一步 package 会补齐）
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

# 阶段2：运行时仅保留 JRE21 + jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/itam-backend-0.0.1.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
