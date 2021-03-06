/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.build

import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.maven.MavenDeployer
import org.gradle.api.tasks.Upload
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withGroovyBuilder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

fun Project.configureMavenArtifactUpload(extension: SupportLibraryExtension) {
    afterEvaluate {
        if (extension.publish) {
            val mavenGroup = extension.mavenGroup
            if (mavenGroup == null) {
                throw Exception("You must specify mavenGroup for $name project")
            }
            if (extension.mavenVersion == null) {
                throw Exception("You must specify mavenVersion for $name project")
            }
            val strippedGroupId = mavenGroup.substringAfterLast(".")
            if (mavenGroup.startsWith("androidx") && !name.startsWith(strippedGroupId)) {
                throw Exception("Your artifactId must start with $strippedGroupId")
            }
            group = mavenGroup
        }
    }

    apply(mapOf("plugin" to "maven"))

    // Set uploadArchives options.
    val uploadTask = tasks.getByName("uploadArchives") as Upload

    val repo = uri(rootProject.property("supportRepoOut") as File)
            ?: throw Exception("supportRepoOut not set")

    uploadTask.repositories {
        it.withGroovyBuilder {
            "mavenDeployer" {
                "repository"(mapOf("url" to repo))
            }
        }
    }

    afterEvaluate {
        if (extension.publish) {
            uploadTask.repositories.withType(MavenDeployer::class.java) { mavenDeployer ->
                mavenDeployer.getPom().project {
                    it.withGroovyBuilder {
                        "name"(extension.name)
                        "description"(extension.description)
                        "url"(extension.url)
                        "inceptionYear"(extension.inceptionYear)

                        "licenses" {
                            "license" {
                                "name"("The Apache Software License, Version 2.0")
                                "url"("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                "distribution"("repo")
                            }
                            for (license in extension.getLicenses()) {
                                "license" {
                                    "name"(license.name)
                                    "url"(license.url)
                                    "distribution"("repo")
                                }
                            }
                        }

                        "scm" {
                            "url"("http://source.android.com")
                            "connection"(ANDROID_GIT_URL)
                        }

                        "developers" {
                            "developer" {
                                "name"("The Android Open Source Project")
                            }
                        }
                    }
                }

                // TODO(aurimas): remove this when Gradle bug is fixed.
                // https://github.com/gradle/gradle/issues/3170
                uploadTask.doFirst {
                    val allDeps = HashSet<Dependency>()
                    collectDependenciesForConfiguration(allDeps, this, "api")
                    collectDependenciesForConfiguration(allDeps, this, "implementation")
                    collectDependenciesForConfiguration(allDeps, this, "compile")

                    mavenDeployer.getPom().whenConfigured {
                        it.dependencies.removeAll { dep ->
                            if (dep == null) {
                                return@removeAll false
                            }

                            val getScopeMethod =
                                    dep::class.java.getDeclaredMethod("getScope")
                            getScopeMethod.invoke(dep) as String == "test"
                        }
                        it.dependencies.forEach { dep ->
                            if (dep == null) {
                                return@forEach
                            }

                            val getGroupIdMethod =
                                    dep::class.java.getDeclaredMethod("getGroupId")
                            val groupId: String = getGroupIdMethod.invoke(dep) as String
                            val getArtifactIdMethod =
                                    dep::class.java.getDeclaredMethod("getArtifactId")
                            val artifactId: String = getArtifactIdMethod.invoke(dep) as String

                            if (isAndroidProject(groupId, artifactId, allDeps)) {
                                val setTypeMethod = dep::class.java.getDeclaredMethod("setType",
                                        java.lang.String::class.java)
                                setTypeMethod.invoke(dep, "aar")
                            }
                        }
                    }
                }
            }

            // Register it as part of release so that we create a Zip file for it
            Release.register(this, extension)
        } else {
            uploadTask.enabled = false
        }
    }
}

private fun collectDependenciesForConfiguration(
    androidxDependencies: MutableSet<Dependency>,
    project: Project,
    name: String
) {
    val config = project.configurations.findByName(name)
    if (config != null) {
        config.dependencies.forEach { dep ->
            if (dep.group?.startsWith("androidx.") ?: false) {
                androidxDependencies.add(dep)
            }
        }
    }
}

private fun Project.isAndroidProject(
    groupId: String,
    artifactId: String,
    deps: Set<Dependency>
): Boolean {
    for (dep in deps) {
        if (dep is ProjectDependency) {
            if (dep.group == groupId && dep.name == artifactId) {
                return dep.getDependencyProject().plugins.hasPlugin(LibraryPlugin::class.java)
            }
        } else {
            var projectModules = project.rootProject.extra.get("projects")
                    as ConcurrentHashMap<String, String>
            if (projectModules.contains("${dep.group}:${dep.name}")) {
                val localProjectVersion = project.findProject(
                        projectModules.get("${dep.group}:${dep.name}"))
                if (localProjectVersion != null) {
                    return localProjectVersion.plugins.hasPlugin(LibraryPlugin::class.java)
                }
            }
        }
    }
    return false
}

private const val ANDROID_GIT_URL =
        "scm:git:https://android.googlesource.com/platform/frameworks/support"