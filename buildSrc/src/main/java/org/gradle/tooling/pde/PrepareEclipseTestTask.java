package org.gradle.tooling.pde;

import org.akhikhl.unpuzzle.PlatformConfig;
import org.akhikhl.wuff.PluginUtils;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.model.RemoteTestRunnerClient;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.JavaExecAction;
import pde.test.utils.PDETestListener;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Not really doing anything - it's there to avoid {@link org.gradle.api.tasks.testing.Test} subclassing
 * because we need some context.
 *
 * Possibly can move to {@code ext}
 */
public class PrepareEclipseTestTask extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(PrepareEclipseTestTask.class);

    private JavaExecAction javaExecHandleBuilder;

    private Project intImageTestProject;

    private String testPluginName;

    public PrepareEclipseTestTask() {
        javaExecHandleBuilder = new DefaultJavaExecAction(getFileResolver());
    }

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

    @TaskAction
    void executeTests() {
        checkPreconditions();
    }

    private void checkPreconditions() {
        if (getTestPluginName() == null) {
            throw new GradleException("Need to specify testPluginName property.");
        }
    }
}
