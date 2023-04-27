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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.gradle.search.FindDependency;

import java.util.List;

/**
 * @author Fabian Krüger
 */
public class ParseGradleFileTest {
    @Test
    void parseGradleFile() {
        String gradleFile = """
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

        List<G.CompilationUnit> parse = GradleParser.builder().build().parse(gradleFile);
        RecipeRun run = new FindDependency("org.springframework.boot", "spring-boot-starter-web", null).run(parse);
        run.getDataTables();
    }

}
