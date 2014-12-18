package org.gradle.tooling.pde;

import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;

import javax.inject.Inject;
import java.io.File;

public class EclipseTestExtension {
    private Project intImageTestProject;

    private String testPluginName;

    /**
     * Application launched in Eclipse.
     * {@code org.eclipse.pde.junit.runtime.coretestapplication} can be used to run non-UI tests.
     */
    private String applicationName = "org.eclipse.pde.junit.runtime.uitestapplication";

    private File optionsFile;

    /** Boolean toggle to control whether to show Eclipse log or not. */
    private boolean consoleLog;

    private long testTimeoutSeconds = 60 * 60L;

    @Inject
    public FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    public Project getIntImageTestProject() {
        return intImageTestProject;
    }

    public void setIntImageTestProject(Project intImageTestProject) {
        this.intImageTestProject = intImageTestProject;
    }

    public String getTestPluginName() {
        return testPluginName;
    }

    public void setTestPluginName(String testPluginName) {
        this.testPluginName = testPluginName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public File getOptionsFile() {
        return optionsFile;
    }

    public void setOptionsFile(File optionsFile) {
        this.optionsFile = optionsFile;
    }

    public boolean isConsoleLog() {
        return consoleLog;
    }

    public void setConsoleLog(boolean consoleLog) {
        this.consoleLog = consoleLog;
    }

    public long getTestTimeoutSeconds() {
        return testTimeoutSeconds;
    }

    public void setTestTimeoutSeconds(long testTimeoutSeconds) {
        this.testTimeoutSeconds = testTimeoutSeconds;
    }
}
