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
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.marker.GradleProjectBuilder;
import org.openrewrite.gradle.toolingapi.GradlePluginDescriptor;
import org.openrewrite.gradle.toolingapi.OpenRewriteModel;
import org.openrewrite.gradle.toolingapi.OpenRewriteModelBuilder;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.gradle.search.FindDependency;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;


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

    @Language("groovy")
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
                      /*      
            tasks.named('test') {
                useJUnitPlatform()
            }
            */
            """;

    @Test
    void parseGradleFile() {

        List<G.CompilationUnit> parse = GradleParser.builder().build().parse(GRADLE_GROOVY_FILE);
        RecipeRun run = new FindDependency("org.springframework.boot", "spring-boot-starter-web", null).run(parse);
        run.getDataTables();
    }

    @Test
    void jimfsTest() throws IOException {
        FileSystem fs = Jimfs.newFileSystem(Configuration.osX());
        Path foo = fs.getPath("/foo");
        Files.createDirectory(foo);

        Files.writeString(foo.resolve("some.txt"), "Yeah!");

        System.out.println("File exists: %s".formatted(Files.exists(foo.resolve("some.txt"))));
        String content = Files.readString(foo.resolve("some.txt"));
        System.out.println("File contains: %s".formatted(content));

//        File file = new File(foo.toString());
//        System.out.println("Path to root is: " + foo);
//        Arrays.stream(file.listFiles()).map(File::getAbsolutePath).collect(Collectors.joining());
    }

    @Test
    void test_renameMe(@TempDir Path pathToStore) throws IOException {
        FileSystem fileSystem = Jimfs.newFileSystem(Configuration.osX());
//        Path pathToStore = fileSystem.getPath("/experiment");
//        Files.createDirectory(pathToStore);
        String fileName = "build.gradle";
        Path gradleBuildFile = pathToStore.resolve(fileName);
        Files.writeString(gradleBuildFile, GRADLE_GROOVY_FILE);
        assertThat(Files.readString(gradleBuildFile)).isEqualTo(GRADLE_GROOVY_FILE);

        // "openrewrite-tooling.gradle"
        Files.writeString(pathToStore.resolve("openrewrite-tooling.gradle"), """
                initscript {
                    repositories {
                        mavenLocal()
                        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
                        mavenCentral()
                    }
                                
                    configurations.all {
                        resolutionStrategy {
                            cacheChangingModulesFor 0, 'seconds'
                            cacheDynamicVersionsFor 0, 'seconds'
                        }
                    }
                                
                    dependencies {
                        classpath 'org.openrewrite.gradle.tooling:plugin:latest.integration'
                        classpath 'org.openrewrite:rewrite-maven:latest.integration'
                    }
                }
                                
                allprojects {
                    apply plugin: org.openrewrite.gradle.toolingapi.ToolingApiOpenRewriteModelPlugin
                }
                """);

        System.out.println("Files in dir: ");
        String files = Files.list(pathToStore).map(Path::toString).collect(Collectors.joining("\n"));
        System.out.println(files);

        OpenRewriteModel openRewriteGradleModel = OpenRewriteModelBuilder.forProjectDirectory(pathToStore.toFile());

        System.out.println("All plugin ids from model: openRewriteGradleModel.gradleProject().getPlugins()");
        System.out.println(openRewriteGradleModel.gradleProject().getPlugins().stream().map(GradlePluginDescriptor::getId).collect(
                Collectors.joining("\n")));

        GradleProject gradleProject = GradleProject.fromToolingModel(openRewriteGradleModel.gradleProject());


        List<G.CompilationUnit> gradleFiles = GradleParser.builder().build().parse(Files.readString(gradleBuildFile));
        G.CompilationUnit gradleFile = gradleFiles.get(0);
        Markers markers = gradleFile.getMarkers();
        markers = markers.add(gradleProject);
        gradleFile = gradleFile.withMarkers(markers);
//        gradleFile.getMarkers().add(gradleProject);
        Optional<GradleProject> optionalGradleMarker = gradleFile.getMarkers().findFirst(GradleProject.class);

        if(!optionalGradleMarker.isPresent()) {
            fail("No marker found");
        }

        List<GradleDependencyConfiguration> config = (List<GradleDependencyConfiguration>) optionalGradleMarker
                .get()
                .getConfigurations();

        String dependenciesString = config.stream().flatMap(c -> c.getRequested().stream()).map(d -> d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion()).collect(Collectors.joining("\n"));
        System.out.println("List of dependencies from GradleProject marker: gradleFile.getMarkers().findFirst(GradleProject.class).get().getConfigurations()");
        System.out.println(dependenciesString);
//        gradleFile.withMarke
//        rs(gradleFile.getMarkers().add(openRewriteGradleModel));

        // Problem: Creating the gradle marker requires a org.gradle.api.Project here, where does it come from?
//        GradleProject gp = GradleProjectBuilder.gradleProject(subproject);
//        gradleFile.withMarkers(gradleFile.getMarkers().add(gp));

        
        
        

        

/*
        // playing with Gradle tooling API (no OpenRewrite)
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
        System.out.println(gradlegradleFile);

        ModelBuilder<OpenRewriteModel> model = connection.model(OpenRewriteModel.class);

         */


    }

}
