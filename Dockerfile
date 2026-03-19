FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# 复制数据文件和源码
COPY java/ ./java/

# 编译
RUN mkdir -p out/production/java && \
    javac -encoding UTF-8 -d out/production/java java/src/CampusNavSystem.java

EXPOSE 8080

CMD ["java", "-cp", "out/production/java", "CampusNavSystem"]
