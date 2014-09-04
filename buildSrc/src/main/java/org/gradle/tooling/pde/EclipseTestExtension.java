package org.gradle.tooling.pde;

import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;

import javax.inject.Inject;

/**
 * Created by radim on 9/4/14.
 */
public class EclipseTestExtension {
    private Project intImageTestProject;

    private String testPluginName;

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
}
