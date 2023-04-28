/*
 * Copyright 2021 - 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.sbm.build.gradle;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.junit.jupiter.api.Test;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.gradle.toolingapi.OpenRewriteModel;
import org.openrewrite.gradle.toolingapi.OpenRewriteModelBuilder;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.gradle.search.FindDependency;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Fabian Krüger
 */
public class ParseGradleFileTest {

    public static final String GRADLE_KOTLIN_FILE = """
            plugins {
            	java
            	id("org.springframework.boot") version "3.0.6"
            	id("io.spring.dependency-management") version "1.1.0"
            }
                        
            group = "com.acme"
            version = "0.0.1-SNAPSHOT"
            java.sourceCompatibility = JavaVersion.VERSION_17
                        
            repositories {
            	mavenCentral()
            }
                        
            dependencies {
            	implementation("org.springframework.boot:spring-boot-starter")
            	implementation("org.springframework.kafka:spring-kafka")
            	testImplementation("org.springframework.boot:spring-boot-starter-test")
            	testImplementation("org.springframework.kafka:spring-kafka-test")
            }
                        
            tasks.withType<Test> {
            	useJUnitPlatform()
            }
            """;

    public static final String GRADLE_GROOVY_FILE = """
            plugins {
                id 'java'
                id 'org.springframework.boot' version '2.7.11'
                id 'io.spring.dependency-management' version '1.0.15.RELEASE'
            }
                            
            group = 'com.example'
            version = '0.0.1-SNAPSHOT'
            sourceCompatibility = '17'
                            
            repositories {
                mavenCentral()
            }
                            
            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter-web'
                testImplementation 'org.springframework.boot:spring-boot-starter-test'
            }
                            
            tasks.named('test') {
                useJUnitPlatform()
            }
            """;

    @Test
    void parseGradleFile() {

        List<G.CompilationUnit> parse = GradleParser.builder().build().parse(GRADLE_GROOVY_FILE);
        RecipeRun run = new FindDependency("org.springframework.boot", "spring-boot-starter-web", null).run(parse);
        run.getDataTables();
    }

    @Test
    void test_renameMe() throws IOException {
        FileSystem fileSystem = Jimfs.newFileSystem(Configuration.osX());
        String fileName = "build.gradle.kts";
        Path pathToStore = fileSystem.getPath("");


        Path filePath = pathToStore.resolve(fileName);
        Files.writeString(filePath, GRADLE_KOTLIN_FILE);
        assertThat(Files.readString(filePath)).isEqualTo(GRADLE_KOTLIN_FILE);
        File projectDir = new File(pathToStore.toString());
//        OpenRewriteModel openRewriteGradleModel = OpenRewriteModelBuilder.forProjectDirectory(projectDir);
//        assertThat(openRewriteGradleModel.gradleProject().getPlugins()).hasSize(4);
//        System.out.println(openRewriteGradleModel.gradleProject().getPlugins());

        DefaultGradleConnector connector = (DefaultGradleConnector) GradleConnector.newConnector();
        GradleConnector gradleConnector = connector.forProjectDirectory(projectDir);
        gradleConnector.useGradleVersion("7.4.2");
        ProjectConnection connection = gradleConnector.connect();
        BuildEnvironment environment = connection.model(BuildEnvironment.class).get();
        System.out.println("==== Environment");
        System.out.println(environment.getGradle().getGradleVersion());
        System.out.println(environment.getGradle().getGradleUserHome());
        System.out.println(environment.getBuildIdentifier().getRootDir());
        System.out.println(environment.getJava().getJavaHome());

        System.out.println("==== GradleProject");
        GradleProject gradleProject = connection.model(GradleProject.class).get();
        gradleProject.getTasks().getAll().stream()
                .forEach(t -> System.out.println(t));
        System.out.println(gradleProject.getBuildScript().getSourceFile());

    }

}
