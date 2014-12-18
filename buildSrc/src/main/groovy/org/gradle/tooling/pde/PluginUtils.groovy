package org.gradle.tooling.pde;

import org.gradle.api.Project;

class PluginUtils {
    static final String eclipsePluginMask = /([\da-zA-Z_.-]+?)-((\d+\.)+[\da-zA-Z_.-]*)/
    static final String osgiFrameworkPluginName = 'org.eclipse.osgi'
    static final String equinoxLauncherPluginName = 'org.eclipse.equinox.launcher'

    static File getEquinoxLauncherFile(Project project) {
        return new File(project.tasks.findByPath('eclipseInstallation').destinationDir, 'plugins').listFiles().find { it.name.startsWith(equinoxLauncherPluginName + '_') }
    }
    static String getPluginName(String fileName) {
        return fileName.replaceAll(eclipsePluginMask, '$1')
    }
}
