## 设置基础镜像
FROM docker.io/library/openjdk:latest
## 设置时区
ENV TZ Asia/Shanghai
## Set the working directory inside the container
WORKDIR /app
# Copy the packaged jar file from the build stage
COPY ./message-1.0.0.jar /app/app.jar
# Set the entry point to run the jar file
CMD ["java", "-jar", "app.jar"]