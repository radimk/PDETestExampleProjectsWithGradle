package org.gradle.tooling.pde;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject

class EclipseTestPlugin implements Plugin<Project> {

    public final FileResolver fileResolver;

    @Inject
    public EclipseTestPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public void apply(Project project) {
        project.extensions.create('eclipseTestExt', EclipseTestExtension)
        project.getPlugins().apply(JavaPlugin)
        project.getPlugins().apply('org.akhikhl.wuff.eclipse-ide-bundle')
        // TODO cannot apply together with this and it means we need to have separate project to assemble testing Eclipse instance
        // project.getPlugins().apply('org.akhikhl.wuff.eclipse-ide-app')

        createSourceSet(project)
        createConfigurations(project)
        createTestTask(project)
    }

    def createSourceSet(Project project) {
        SourceSet sourceSet = project.sourceSets.create('eclipseTest')
        sourceSet.compileClasspath += project.sourceSets.main.output + project.sourceSets.test.output
        sourceSet.runtimeClasspath += project.sourceSets.main.output + project.sourceSets.test.output
        sourceSet.java.srcDir('src/main/java')
        sourceSet.resources.srcDir('src/main/resources')
    }

    def createConfigurations(Project project) {
        project.configurations {
            eclipseTestCompile.extendsFrom testCompile
            eclipseTestRuntime.extendsFrom testRuntime
        }
    }

    def createTestTask(Project project) {
        def eclipseTest = project.tasks.create('eclipseTest', Test)
        eclipseTest.testExecuter = new EclipseTestExecuter()

        eclipseTest.testClassesDir = project.sourceSets.eclipseTest.output.classesDir
        eclipseTest.classpath = project.sourceSets.eclipseTest.runtimeClasspath
        eclipseTest.testSrcDirs = []
        // eclipseTest.jvmArgs '-Xmx512m', '-XX:MaxPermSize=256m', '-XX:+HeapDumpOnOutOfMemoryError'
        eclipseTest.reports.html.destination = project.file("${project.reporting.baseDir}/eclipseTest")

    }
}
