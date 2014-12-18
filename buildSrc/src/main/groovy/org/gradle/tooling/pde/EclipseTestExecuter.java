package org.gradle.tooling.pde;

import org.akhikhl.unpuzzle.PlatformConfig;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.model.RemoteTestRunnerClient;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestClassScanner;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.TrueTimeProvider;
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
        File equinoxLauncherFile = org.akhikhl.wuff.PluginUtils.getEquinoxLauncherFile(getIntImageTestProject(testTask));
        LOGGER.info("equinox launcher file {}", equinoxLauncherFile);

        final JavaExecAction javaExecHandleBuilder = new DefaultJavaExecAction(getFileResolver(testTask));
        javaExecHandleBuilder.setClasspath(getIntImageTestProject(testTask).files(
                equinoxLauncherFile));
                // new File(runPluginsDir, equinoxLauncherFile.getName().replaceAll(PluginUtils.getEclipsePluginMask(), "$1_$2"))));
        javaExecHandleBuilder.setMain("org.eclipse.equinox.launcher.Main");
        List<String> programArgs = new ArrayList<String>();
        // TODO how much do we need? -os linux -ws gtk -arch x86_64 -nl en_US
        programArgs.add("-os");
        programArgs.add(PlatformConfig.current_os_filesystem_suffix);
        if ("linux".equals(PlatformConfig.current_os)) {
            programArgs.add("-ws");
            programArgs.add("gtk");
        } else if ("windows".equals(PlatformConfig.current_os)) {
            programArgs.add("-ws");
            programArgs.add("win32");
        }
        programArgs.add("-arch");
        programArgs.add(PlatformConfig.current_arch);

        if (getExtension(testTask).isConsoleLog()) {
            programArgs.add("-consoleLog");
        }
        File optionsFile = getExtension(testTask).getOptionsFile();
        if (optionsFile != null) {
            programArgs.add("-debug");
            programArgs.add(optionsFile.getAbsolutePath());
        }
        programArgs.add("-version");
        programArgs.add("3");
        programArgs.add("-port");
        programArgs.add(Integer.toString(pdeTestPort));
        programArgs.add("-testLoaderClass");
        programArgs.add("org.eclipse.jdt.internal.junit4.runner.JUnit4TestLoader");
        programArgs.add("-loaderpluginname");
        programArgs.add("org.eclipse.jdt.junit4.runtime");
        programArgs.add("-classNames");
        for (String clzName : collectTestNames(testTask)) {
            programArgs.add(clzName);
        }
        programArgs.add("-application");
        programArgs.add(getExtension(testTask).getApplicationName());
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
        javaExecHandleBuilder.setSystemProperties(testTask.getSystemProperties());
        javaExecHandleBuilder.setEnvironment(testTask.getEnvironment());

        // TODO this should be specified when creating the task (to allow overrid in build script)
        List<String> jvmArgs = new ArrayList<String>();
        jvmArgs.add("-XX:MaxPermSize=256m");
        jvmArgs.add("-Xms40m");
        jvmArgs.add("-Xmx512m");

        // uncomment to debug spawned Eclipse instance
        // jvmArgs.add("-Xdebug");
        // jvmArgs.add("-Xrunjdwp:transport=dt_socket,address=8998,server=y");

        if (PlatformConfig.current_os == "macosx") {
            jvmArgs.add("-XstartOnFirstThread");
        }
        javaExecHandleBuilder.setJvmArgs(jvmArgs);
        javaExecHandleBuilder.setWorkingDir(testTask.getProject().getBuildDir());

        final CountDownLatch latch = new CountDownLatch(1);
        Future<?> eclipseJob = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ExecResult execResult = javaExecHandleBuilder.execute();
                    execResult.assertNormalExitValue();
                } finally {
                    latch.countDown();
                }
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
                    } finally {
                        latch.countDown();
                    }
                }
            }
        });
        try {
            latch.await(getExtension(testTask).getTestTimeoutSeconds(), TimeUnit.SECONDS);
            // short chance to do cleanup
            eclipseJob.get(15, TimeUnit.SECONDS);
            testCollectorJob.get(15, TimeUnit.SECONDS);
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
        return testTask.getProject().getPlugins().findPlugin(EclipseTestPlugin.class).fileResolver;
    }

    private List<String> collectTestNames(Test testTask) {
        ClassNameCollectingProcessor processor = new ClassNameCollectingProcessor();
        Runnable detector;
        final FileTree testClassFiles = testTask.getCandidateClassFiles();
        if (testTask.isScanForTestClasses()) {
            TestFrameworkDetector testFrameworkDetector = testTask.getTestFramework().getDetector();
            testFrameworkDetector.setTestClassesDirectory(testTask.getTestClassesDir());
            testFrameworkDetector.setTestClasspath(testTask.getClasspath());
            detector = new DefaultTestClassScanner(testClassFiles, testFrameworkDetector, processor);
        } else {
            detector = new DefaultTestClassScanner(testClassFiles, null, processor);
        }
        new TestMainAction(detector, processor, new NoopTestResultProcessor(), new TrueTimeProvider()).run();
        LOGGER.debug("collected test class names: {}", processor.classNames);
        return processor.classNames;
    }

    public static class NoopTestResultProcessor implements TestResultProcessor {

        @Override
        public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
        }

        @Override
        public void completed(Object o, TestCompleteEvent testCompleteEvent) {
        }

        @Override
        public void output(Object o, TestOutputEvent testOutputEvent) {
        }

        @Override
        public void failure(Object o, Throwable throwable) {
        }
    }

    private class ClassNameCollectingProcessor implements TestClassProcessor {
        public List<String> classNames = new ArrayList<String>();

        @Override
        public void startProcessing(TestResultProcessor testResultProcessor) {
            // no-op
        }

        @Override
        public void processTestClass(TestClassRunInfo testClassRunInfo) {
            classNames.add(testClassRunInfo.getTestClassName());
        }

        @Override
        public void stop() {
            // no-op
        }
    }
}
