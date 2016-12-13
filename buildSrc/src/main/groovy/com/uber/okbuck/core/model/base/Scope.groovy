package com.uber.okbuck.core.model.base

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.uber.okbuck.OkBuckGradlePlugin
import com.uber.okbuck.core.dependency.DependencyCache
import com.uber.okbuck.core.dependency.ExternalDependency
import com.uber.okbuck.core.model.android.AndroidLibTarget
import com.uber.okbuck.core.model.groovy.GroovyLibTarget
import com.uber.okbuck.core.model.java.JavaLibTarget
import com.uber.okbuck.core.model.jvm.JvmTarget
import com.uber.okbuck.core.util.FileUtil
import com.uber.okbuck.core.util.ProjectUtil
import groovy.transform.EqualsAndHashCode
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnknownConfigurationException

@EqualsAndHashCode
class Scope {

    final String resourcesDir
    final Set<String> sources
    final Set<Target> targetDeps = [] as Set
    List<String> jvmArgs
    DependencyCache depCache

    protected final Project project
    protected final Set<ExternalDependency> external = [] as Set
    protected final Set<ExternalDependency> firstLevel = [] as Set

    Scope(Project project,
          Collection<String> configurations,
          Set<File> sourceDirs = [],
          File resDir = null,
          List<String> jvmArguments = [],
          DependencyCache depCache = OkBuckGradlePlugin.depCache) {

        this.project = project
        sources = FileUtil.getAvailable(project, sourceDirs)
        resourcesDir = FileUtil.getAvailableFile(project, resDir)
        jvmArgs = jvmArguments
        this.depCache = depCache

        extractConfigurations(configurations)
    }

    Set<String> getExternalDeps() {
        external.collect { ExternalDependency dependency ->
            depCache.get(dependency)
        }
    }

    Set<String> getPackagedLintJars() {
        external.findAll { ExternalDependency dependency ->
            depCache.getLintJar(dependency) != null
        }.collect { ExternalDependency dependency ->
            depCache.getLintJar(dependency)
        }
    }

    Set<String> getAnnotationProcessors() {
        (firstLevel.collect {
            depCache.getAnnotationProcessors(it)
        } + targetDeps.collect { Target target ->
            (List<String>) target.getProp(project.rootProject.okbuck.annotationProcessors, null)
        }.findAll { List<String> processors ->
            processors != null
        }).flatten() as Set<String>
    }

    private void extractConfigurations(Collection<String> configurations) {
        List<Configuration> validConfigurations = []
        configurations.each { String configName ->
            try {
                Configuration configuration = project.configurations.getByName(configName)
                validConfigurations.add(configuration)
            } catch (UnknownConfigurationException ignored) {
            }
        }

        Set<ResolvedArtifact> artifacts = validConfigurations.collect {
            it.resolvedConfiguration.resolvedArtifacts
        }.flatten() as Set<ResolvedArtifact>

        validConfigurations.collect {
            it.resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency resolvedDependency ->
                ResolvedArtifact artifact = resolvedDependency.moduleArtifacts[0]
                if (!artifact.id.componentIdentifier.displayName.contains(" ")) {
                    firstLevel.add(new ExternalDependency(artifact.moduleVersion.id, artifact.file))
                }
            }
        }

        Set<File> files = validConfigurations.collect {
            it.files
        }.flatten() as Set<File>

        Set<File> resolvedFiles = [] as Set
        artifacts.each { ResolvedArtifact artifact ->
            String identifier = artifact.id.componentIdentifier.displayName
            File dep = artifact.file

            resolvedFiles.add(dep)

            if (identifier.contains(" ")) {
                Project targetProject = project.project(identifier.replaceFirst("project ", ""))
                Target target = getTargetForOutput(targetProject, dep)
                if (target) {
                    targetDeps.add(target)
                }
            } else {
                external.add(new ExternalDependency(artifact.moduleVersion.id, dep))
            }
        }

        files.findAll { File resolved ->
            !resolvedFiles.contains(resolved)
        }.each { File localDep ->
            external.add(ExternalDependency.fromLocal(localDep))
        }

    }

    @SuppressWarnings("GrReassignedInClosureLocalVar")
    static Target getTargetForOutput(Project targetProject, File output) {
        Target result = null
        ProjectType type = ProjectUtil.getType(targetProject)
        switch (type) {
            case ProjectType.ANDROID_LIB:
                def baseVariants = targetProject.android.libraryVariants
                baseVariants.all { BaseVariant baseVariant ->
                    def variant = baseVariant.outputs.find { BaseVariantOutput out ->
                        (out.outputFile == output)
                    }
                    if (variant != null) {
                        result = new AndroidLibTarget(targetProject, variant.name)
                    }
                }
                break
            case ProjectType.GROOVY_LIB:
                result = new GroovyLibTarget(targetProject, JvmTarget.MAIN)
                break
            case ProjectType.JAVA_APP:
            case ProjectType.JAVA_LIB:
                result = new JavaLibTarget(targetProject, JvmTarget.MAIN)
                break
            default:
                result = null
        }
        return result
    }
}
