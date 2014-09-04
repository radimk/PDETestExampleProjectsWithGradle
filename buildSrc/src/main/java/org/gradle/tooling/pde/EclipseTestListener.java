package org.gradle.tooling.pde;

import junit.framework.TestCase;
import junit.framework.TestResult;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.internal.tasks.testing.results.AttachParentTestResultProcessor;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EclipseTestListener implements ITestRunListener2 {
    private static final Pattern ECLIPSE_TEST_NAME = Pattern.compile("(.*)\\((.*)\\)");
    private final TestResultProcessor testResultProcessor;
    private String suiteName;
    private TestDescriptorInternal currentTestClass;
    private TestDescriptorInternal currentTestMethod;
    private DefaultTestSuiteDescriptor currentSuite;
    private org.gradle.api.tasks.testing.TestResult.ResultType currentResult = null;
    private final Object waitMonitor;

    public EclipseTestListener(TestResultProcessor testResultProcessor, String suite, Object waitMonitor) {
        this.testResultProcessor = new AttachParentTestResultProcessor(testResultProcessor);
        this.waitMonitor = waitMonitor;
        suiteName = suite;
    }

    public synchronized void testRunStarted(int testCount) {
        currentSuite = new DefaultTestSuiteDescriptor("root", suiteName);

        testResultProcessor.started(currentSuite, new TestStartEvent(System.currentTimeMillis()));
    }

    public synchronized void testRunEnded(long elapsedTime) {
//        System.out.println("Test Run Ended   - " + (failed() ? "FAILED" : "PASSED") + " - Total: " + totalNumberOfTests
//                + " (Errors: " + numberOfTestsWithError
//                + ", Failed: " + numberOfTestsFailed
//                + ", Passed: " + numberOfTestsPassed + "), duration " + elapsedTime + "ms." + " id: " + currentSuite.getId());

        testResultProcessor.completed(currentSuite.getId(), new TestCompleteEvent(System.currentTimeMillis()));
        synchronized (waitMonitor) {
            waitMonitor.notifyAll();
        }
    }

    public synchronized void testRunStopped(long elapsedTime) {
        // System.out.println("Test Run Stopped");
        // TODO report failure when stopped?
        testRunEnded(elapsedTime);
    }

    public synchronized void testRunTerminated() {
        // System.out.println("Test Run Terminated");
        // TODO report failure when terminated?
        testRunEnded(0);
    }

    public synchronized void testStarted(String testId, String testName) {
        // TODO need idGenerator
        String testClass = testName;
        String testMethod = testName;
        Matcher matcher = ECLIPSE_TEST_NAME.matcher(testName);
        if (matcher.matches()) {
            testClass = matcher.group(2);
            testMethod = matcher.group(1);
        }

        currentTestClass = new DefaultTestClassDescriptor(testId + " class"/* idGenerator.generateId() */, testClass);
        currentTestMethod = new DefaultTestMethodDescriptor(testId/* idGenerator.generateId() */, testClass, testMethod);
        currentResult = org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS;
        testResultProcessor.started(currentTestClass, new TestStartEvent(System.currentTimeMillis()));
        testResultProcessor.started(currentTestMethod, new TestStartEvent(System.currentTimeMillis()));
    }

    public synchronized void testEnded(String testId, String testName) {
        testResultProcessor.completed(currentTestMethod.getId(), new TestCompleteEvent(System.currentTimeMillis(), currentResult));
        testResultProcessor.completed(currentTestClass.getId(), new TestCompleteEvent(System.currentTimeMillis()));
    }

    public synchronized void testFailed(int status, String testId, String testName, String trace, String expected, String actual) {
        String statusMessage = String.valueOf(status);

        if (status == ITestRunListener2.STATUS_OK) {
            statusMessage = "OK";
            currentResult = org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS;
        } else if (status == ITestRunListener2.STATUS_FAILURE) {
            statusMessage = "FAILED";
            currentResult = org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE;
        } else if (status == ITestRunListener2.STATUS_ERROR) {
            statusMessage = "ERROR";
            currentResult = org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE;
        }
        // System.out.println("  Test Failed  - " + count() + " - " + testName + " - status: " + statusMessage
        //         + ", trace: " + trace + ", expected: " + expected + ", actual: " + actual + " id: " + currentTestClass.getId());

        testResultProcessor.output(currentTestMethod.getId(),
                new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, "Expected: " + expected + ", actual: " + actual));
        testResultProcessor.failure(currentTestMethod.getId(), new FailureThrowableStub(trace));
	}

	public synchronized void testReran(String testId, String testClass, String testName, int status, String trace, String expected, String actual) {
        throw new UnsupportedOperationException("Unexpected call to testReran when running tests in Eclipse.");
	}

	public synchronized void testTreeEntry(String description) {
        // System.out.println("Test Tree Entry - Description: " + description);
    }

    public static class FailureThrowableStub extends Exception {
        private final String trace;
        public FailureThrowableStub(String trace) {
            super();
            this.trace = trace;
        }

        @Override
        public void printStackTrace(PrintStream s) {
            s.println(trace);
        }

        @Override
        public void printStackTrace(PrintWriter s) {
            s.println(trace);
        }
    }
}
