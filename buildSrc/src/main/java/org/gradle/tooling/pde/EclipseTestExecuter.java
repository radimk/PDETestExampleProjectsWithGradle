package org.gradle.tooling.pde;

import org.akhikhl.unpuzzle.PlatformConfig;
import org.akhikhl.wuff.PluginUtils;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.model.RemoteTestRunnerClient;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.JavaExecAction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class EclipseTestExecuter implements TestExecuter {
    private static final Logger LOGGER = Logging.getLogger(EclipseTestExecuter.class);

    @Override
    public void execute(Test test, TestResultProcessor testResultProcessor) {
        LOGGER.info("Executing tests in Eclipse");
        checkPreconditions(test);

        int pdeTestPort = new PDETestPortLocator().locatePDETestPortNumber();
        if (pdeTestPort == -1) {
            throw new GradleException("Cannot allocate port for PDE test run");
        }
        LOGGER.info("Will use port {} to communicate with Eclipse.", pdeTestPort);

        runPDETestsInEclipse(test, testResultProcessor, pdeTestPort);
    }

    private EclipseTestExtension getExtension(Test testTask) {
        return (EclipseTestExtension) testTask.getProject().getExtensions().findByName("eclipseTestExt");
    }

    private void checkPreconditions(Test test) {
        if (getExtension(test).getTestPluginName() == null) {
            throw new GradleException("Need to specify testPluginName property.");
        }
    }

    private void runPDETestsInEclipse(final Test testTask, final TestResultProcessor testResultProcessor, final int pdeTestPort) {
        ExecutorService threadPool = Executors.newFixedThreadPool(2);

        // TODO delete workspace before run?
        File runDir = new File(testTask.getProject().getBuildDir(), testTask.getName());

        // TODO fix the dependency
        // File configIniFile = getInputs().getFiles().getSingleFile();
        File configIniFile = new File(getIntImageTestProject(testTask).getBuildDir(), "run/configuration/config.ini");

        File runPluginsDir = new File(configIniFile, "../../plugins");
        LOGGER.info("Will use config ini file {} from project {}, plugins dir {}", configIniFile, getIntImageTestProject(testTask), runPluginsDir);
        File equinoxLauncherFile = PluginUtils.getEquinoxLauncherFile(getIntImageTestProject(testTask));

        final JavaExecAction javaExecHandleBuilder = new DefaultJavaExecAction(getFileResolver(testTask));
        javaExecHandleBuilder.setClasspath(getIntImageTestProject(testTask).files(
                new File(runPluginsDir, equinoxLauncherFile.getName().replaceAll(PluginUtils.getEclipsePluginMask(), "$1_$2"))));
        javaExecHandleBuilder.setMain("org.eclipse.equinox.launcher.Main");
        List<String> programArgs = new ArrayList<String>();
        // TODO how much do we need? -os linux -ws gtk -arch x86_64 -nl en_US
        programArgs.add("-os");
        programArgs.add("linux");
        programArgs.add("-ws");
        programArgs.add("gtk");
        programArgs.add("-arch");
        programArgs.add("x86_64");

        // programArgs.add("-consoleLog");
        programArgs.add("-version");
        programArgs.add("3");
        programArgs.add("-port");
        programArgs.add(Integer.toString(pdeTestPort));
        programArgs.add("-testLoaderClass");
        programArgs.add("org.eclipse.jdt.internal.junit4.runner.JUnit4TestLoader");
        programArgs.add("-loaderpluginname");
        programArgs.add("org.eclipse.jdt.junit4.runtime");
        // TODO generate list of test classes
        programArgs.add("-classNames");
        programArgs.add("phonebookexample.dialogs.PhoneBookEntryEditorDialogTest");
        programArgs.add("-application");
        // TODO allow to run also non-UI tests
        programArgs.add("org.eclipse.pde.junit.runtime.uitestapplication"); // or org.eclipse.pde.junit.runtime.coretestapplication
        programArgs.add("-product org.eclipse.platform.ide");
        // alternatively can use URI for -data and -configuration (file:///path/to/dir/)
        programArgs.add("-data");
        programArgs.add(runDir.getAbsolutePath());
        programArgs.add("-configuration");
        programArgs.add(configIniFile.getParentFile().getAbsolutePath());

        // TODO get from current project
        programArgs.add("-testpluginname");
        programArgs.add(getExtension(testTask).getTestPluginName());

        javaExecHandleBuilder.setArgs(programArgs);
        List<String> jvmArgs = new ArrayList<String>();
        jvmArgs.add("-Dosgi.requiredJavaVersion=1.7");
        jvmArgs.add("-XX:MaxPermSize=256m");
        jvmArgs.add("-Xms40m");
        jvmArgs.add("-Xmx512m");
        jvmArgs.add("-Declipse.pde.launch=true");
        jvmArgs.add("-Declipse.p2.data.area=@config.dir/p2");
        jvmArgs.add("-Dfile.encoding=UTF-8");
        if(PlatformConfig.current_os == "macosx") {
            jvmArgs.add("-XstartOnFirstThread");
        }
        javaExecHandleBuilder.setJvmArgs(jvmArgs);
        javaExecHandleBuilder.setWorkingDir(testTask.getProject().getBuildDir());

        Future<?> eclipseJob = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                ExecResult execResult = javaExecHandleBuilder.execute();
                execResult.assertNormalExitValue();
            }
        });
        //TODO
        final String suiteName = getExtension(testTask).getTestPluginName();
        Future<?> testCollectorJob = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                EclipseTestListener pdeTestListener = new EclipseTestListener(testResultProcessor, suiteName, this);
                new RemoteTestRunnerClient().startListening(new ITestRunListener2[]{pdeTestListener}, pdeTestPort);
                LOGGER.info("Listening on port " + pdeTestPort + " for test suite " + suiteName + " results ...");
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        try {
            eclipseJob.get(5, TimeUnit.MINUTES);
            testCollectorJob.get(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new GradleException("Test execution failed", e);
        } catch (ExecutionException e) {
            throw new GradleException("Test execution failed", e);
        } catch (TimeoutException e) {
            throw new GradleException("Test execution failed", e);
        }
    }

    private Project getIntImageTestProject(Test testTask) {
        return getExtension(testTask).getIntImageTestProject();
    }

    private FileResolver getFileResolver(Test testTask) {
        return testTask.getProject().getPlugins().findPlugin(EclipseTestPlugin.class).getFileResolver();
    }
}
