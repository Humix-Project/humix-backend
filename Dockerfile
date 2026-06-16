# 1. 빌드 완료된 jar 파일을 실행할 JDK 환경 선택
FROM eclipse-temurin:21-jdk-alpine

# 2. 컨테이너 내부에서 작업할 폴더 생성
WORKDIR /app

# 3. gradle 빌드 시 생성되는 jar 파일을 컨테이너 내부로 복사
# (프로젝트 이름이나 버전에 따라 jar 파일명이 달라지므로 * 기호 활용)
COPY build/libs/*-SNAPSHOT.jar app.jar

# 4. 스프링 부트가 사용할 8080 포트 열기
EXPOSE 8080

# 5. 컨테이너가 켜지면 스프링 부트 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]