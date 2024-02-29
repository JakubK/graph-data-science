# Use the official Gradle image as a build image
FROM gradle:8.4-jdk20 AS build
WORKDIR /app
COPY . .

RUN gradle wrapper --gradle-version 8.4