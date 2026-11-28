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
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("com.github.ben-manes.caffeine:caffeine")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 통합 테스트에서 DB 컨테이너의 실행 주기에 맞춰 알림 작업을 직접 호출한다.
	systemProperty("outpick.notifications.poll-delay", "1d")
	// 시연용 장소는 앱의 local 실행에서만 주입하고 통합 테스트 DB는 비워 둔다.
	systemProperty("spring.flyway.locations", "classpath:db/migration")
}

springBoot {
	mainClass = "com.outpick.BackendApplication"
}

tasks.register<JavaExec>("importPlaceOperatingInfo") {
	group = "application"
	description = "플라이스테이션 운영 안내를 로컬 DB에 미검수 참고정보로 최초 수집합니다."
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

// 명시적으로 실행하는 읽기 전용 TourAPI 상세 조사. 서버 시작 시 자동 실행되지 않는다.
tasks.register<JavaExec>("collectTourApiSurvey") {
    group = "application"
    description = "DB를 읽고 TourAPI 상세 응답을 프로젝트 외부 조사 폴더에 저장합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.OutpickTourApiSurvey"
    workingDir = rootProject.projectDir.parentFile
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("importFilteredPlaceCandidates") {
    group = "application"
    description = "정제된 626건의 장소 후보를 기본 Dry Run으로 검증하고, --apply 때만 로컬 검수 테이블에 저장합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.FilteredPlaceCandidateCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("importOfficialPlaceEvidence") {
    group = "application"
    description = "제공된 공식 출처 보완값을 로컬 DB에 검수 대기 사실·가격으로만 저장합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.OfficialPlaceEvidenceCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("migrateLocalPlaceEvidence") {
    group = "application"
    description = "기존 Flyway 마이그레이션을 로컬 DB에 명시적으로 적용합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.LocalSchemaMigrationCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("exportPlaceEvidenceReview") {
    group = "application"
    description = "로컬 DB 장소·상세·가격·중복 검수 현황을 읽기 전용 파일로 추출합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.PlaceEvidenceExportCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("reviewSnorkelingCandidates") {
    group = "application"
    description = "제공된 스노클링 후보를 로컬 DB와 대조하고 검수 대기로만 저장합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.SnorkelingCandidateCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("reviewSnorkelingBeachMatches") {
    group = "application"
    description = "해수욕장 이름·지역 유사도를 일괄 비교해 자동 병합 없는 검수 제안을 생성합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.SnorkelingMatchReviewCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("recoverReviewCoordinates") {
    group = "application"
    description = "검수 파일의 TourAPI 원본 응답에서 일치하는 해수욕장 좌표만 복구합니다. DB는 변경하지 않습니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.ReviewCoordinateRecoveryCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("materializeReviewedBeaches") {
    group = "application"
    description = "검수 파일에서 TourAPI가 유일하게 일치하는 장소만 로컬 Place 초안으로 등록합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.MaterializeReviewedBeachesCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("materializePendingCandidates") {
    group = "application"
    description = "검수 후보를 좌표·운영 상태를 꾸미지 않고 로컬 Place 초안으로 등록합니다."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.MaterializePendingCandidatesCommand"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.register<JavaExec>("exportSharedPlaceMigration") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.outpick.domain.place.importer.SharedPlaceMigrationExport"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}
