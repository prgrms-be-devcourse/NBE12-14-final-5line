plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.outpick"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
	implementation("org.springframework.boot:spring-boot-starter-security")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

springBoot {
	mainClass = "com.outpick.BackendApplication"
}

tasks.register<JavaExec>("importPlaceOperatingInfo") {
	group = "application"
	description = "DB의 TourAPI 장소에 운영 안내를 최초 수집합니다. 기존 안내는 보존합니다."
	classpath = sourceSets.main.get().runtimeClasspath
	mainClass = "com.outpick.domain.place.importer.TourOperatingInfoCommand"
	systemProperty("spring.devtools.restart.enabled", "false")
	javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("importTourPlaces") {
	group = "application"
	description = "TourAPI 초기 후보 500곳을 로컬 DB에 검수 전 상태로 수집합니다."
	classpath = sourceSets.main.get().runtimeClasspath
	mainClass = "com.outpick.domain.place.importer.TourApiImportCommand"
	systemProperty("spring.devtools.restart.enabled", "false")
	javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}
