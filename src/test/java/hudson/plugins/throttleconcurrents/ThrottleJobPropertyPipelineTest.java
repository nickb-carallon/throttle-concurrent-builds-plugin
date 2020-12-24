package hudson.plugins.throttleconcurrents;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;

import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.QueueTaskFuture;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ThrottleJobPropertyPipelineTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public TemporaryFolder firstAgentTmp = new TemporaryFolder();
    @Rule public TemporaryFolder secondAgentTmp = new TemporaryFolder();

    private List<Node> agents = new ArrayList<>();

    /** Clean up agents. */
    @After
    public void tearDown() throws Exception {
        TestUtil.tearDown(j, agents);
        agents = new ArrayList<>();
    }

    @Ignore("TODO Doesn't work at present")
    @Test
    public void onePerNode() throws Exception {
        Node agent = TestUtil.setupAgent(j, firstAgentTmp, agents, null, null, 2, "on-agent");
        TestUtil.setupCategories(TestUtil.ONE_PER_NODE);

        WorkflowJob firstJob = j.createProject(WorkflowJob.class);
        firstJob.setDefinition(getJobFlow("first", agent.getNodeName()));
        firstJob.addProperty(
                new ThrottleJobProperty(
                        null, // maxConcurrentPerNode
                        null, // maxConcurrentTotal
                        Collections.singletonList(TestUtil.ONE_PER_NODE.getCategoryName()),
                        true, // throttleEnabled
                        TestUtil.THROTTLE_OPTION_CATEGORY, // throttleOption
                        false,
                        null,
                        ThrottleMatrixProjectOptions.DEFAULT));

        WorkflowRun firstJobFirstRun = firstJob.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait-first-job/1", firstJobFirstRun);

        WorkflowJob secondJob = j.createProject(WorkflowJob.class);
        secondJob.setDefinition(getJobFlow("second", agent.getNodeName()));
        secondJob.addProperty(
                new ThrottleJobProperty(
                        null, // maxConcurrentPerNode
                        null, // maxConcurrentTotal
                        Collections.singletonList(TestUtil.ONE_PER_NODE.getCategoryName()),
                        true, // throttleEnabled
                        TestUtil.THROTTLE_OPTION_CATEGORY, // throttleOption
                        false,
                        null,
                        ThrottleMatrixProjectOptions.DEFAULT));

        WorkflowRun secondJobFirstRun = secondJob.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Still waiting to schedule task", secondJobFirstRun);
        j.jenkins.getQueue().maintain();
        assertFalse(j.jenkins.getQueue().isEmpty());
        Queue.Item queuedItem =
                Iterables.getOnlyElement(Arrays.asList(j.jenkins.getQueue().getItems()));
        Set<String> blockageReasons = TestUtil.getBlockageReasons(queuedItem.getCauseOfBlockage());
        assertThat(
                blockageReasons,
                hasItem(Messages._ThrottleQueueTaskDispatcher_MaxCapacityOnNode(1).toString()));
        assertEquals(1, agent.toComputer().countBusy());
        TestUtil.hasPlaceholderTaskForRun(agent, firstJobFirstRun);

        SemaphoreStep.success("wait-first-job/1", null);
        j.assertBuildStatusSuccess(j.waitForCompletion(firstJobFirstRun));
        SemaphoreStep.waitForStart("wait-second-job/1", secondJobFirstRun);
        j.jenkins.getQueue().maintain();
        assertTrue(j.jenkins.getQueue().isEmpty());
        assertEquals(1, agent.toComputer().countBusy());
        TestUtil.hasPlaceholderTaskForRun(agent, secondJobFirstRun);
        SemaphoreStep.success("wait-second-job/1", null);
        j.assertBuildStatusSuccess(j.waitForCompletion(secondJobFirstRun));
    }

    @Test
    public void twoTotal() throws Exception {
        Node firstAgent = TestUtil.setupAgent(j, firstAgentTmp, agents, null, null, 4, "on-agent");
        Node secondAgent =
                TestUtil.setupAgent(j, secondAgentTmp, agents, null, null, 4, "on-agent");
        TestUtil.setupCategories(TestUtil.TWO_TOTAL);

        WorkflowJob firstJob = j.createProject(WorkflowJob.class);
        firstJob.setDefinition(getJobFlow("first", firstAgent.getNodeName()));
        firstJob.addProperty(
                new ThrottleJobProperty(
                        null, // maxConcurrentPerNode
                        null, // maxConcurrentTotal
                        Collections.singletonList(TestUtil.TWO_TOTAL.getCategoryName()),
                        true, // throttleEnabled
                        TestUtil.THROTTLE_OPTION_CATEGORY, // throttleOption
                        false,
                        null,
                        ThrottleMatrixProjectOptions.DEFAULT));

        WorkflowRun firstJobFirstRun = firstJob.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait-first-job/1", firstJobFirstRun);

        WorkflowJob secondJob = j.createProject(WorkflowJob.class);
        secondJob.setDefinition(getJobFlow("second", secondAgent.getNodeName()));
        secondJob.addProperty(
                new ThrottleJobProperty(
                        null, // maxConcurrentPerNode
                        null, // maxConcurrentTotal
                        Collections.singletonList(TestUtil.TWO_TOTAL.getCategoryName()),
                        true, // throttleEnabled
                        TestUtil.THROTTLE_OPTION_CATEGORY, // throttleOption
                        false,
                        null,
                        ThrottleMatrixProjectOptions.DEFAULT));

        WorkflowRun secondJobFirstRun = secondJob.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait-second-job/1", secondJobFirstRun);

        WorkflowJob thirdJob = j.createProject(WorkflowJob.class, "third-job");
        thirdJob.setDefinition(getJobFlow("third", "on-agent"));
        thirdJob.addProperty(
                new ThrottleJobProperty(
                        null, // maxConcurrentPerNode
                        null, // maxConcurrentTotal
                        Collections.singletonList(TestUtil.TWO_TOTAL.getCategoryName()),
                        true, // throttleEnabled
                        TestUtil.THROTTLE_OPTION_CATEGORY, // throttleOption
                        false,
                        null,
                        ThrottleMatrixProjectOptions.DEFAULT));

        QueueTaskFuture<WorkflowRun> thirdJobFirstRunFuture = thirdJob.scheduleBuild2(0);
        j.jenkins.getQueue().maintain();
        assertFalse(j.jenkins.getQueue().isEmpty());
        Queue.Item queuedItem =
                Iterables.getOnlyElement(Arrays.asList(j.jenkins.getQueue().getItems()));
        Set<String> blockageReasons = TestUtil.getBlockageReasons(queuedItem.getCauseOfBlockage());
        assertThat(
                blockageReasons,
                hasItem(Messages._ThrottleQueueTaskDispatcher_MaxCapacityTotal(2).toString()));
        assertEquals(1, firstAgent.toComputer().countBusy());
        TestUtil.hasPlaceholderTaskForRun(firstAgent, firstJobFirstRun);

        assertEquals(1, secondAgent.toComputer().countBusy());
        TestUtil.hasPlaceholderTaskForRun(secondAgent, secondJobFirstRun);

        SemaphoreStep.success("wait-first-job/1", null);
        j.assertBuildStatusSuccess(j.waitForCompletion(firstJobFirstRun));

        WorkflowRun thirdJobFirstRun = thirdJobFirstRunFuture.waitForStart();
        SemaphoreStep.waitForStart("wait-third-job/1", thirdJobFirstRun);
        assertTrue(j.jenkins.getQueue().isEmpty());
        assertEquals(2, firstAgent.toComputer().countBusy() + secondAgent.toComputer().countBusy());
        TestUtil.hasPlaceholderTaskForRun(firstAgent, thirdJobFirstRun);

        SemaphoreStep.success("wait-second-job/1", null);
        j.assertBuildStatusSuccess(j.waitForCompletion(secondJobFirstRun));

        SemaphoreStep.success("wait-third-job/1", null);
        j.assertBuildStatusSuccess(j.waitForCompletion(thirdJobFirstRun));
    }

    static CpsFlowDefinition getJobFlow(String jobName, String label) {
        return new CpsFlowDefinition(getThrottleScript(jobName, label), true);
    }

    private static String getThrottleScript(String jobName, String label) {
        return "echo 'hi there'\n"
                + "node('"
                + label
                + "') {\n"
                + "  semaphore 'wait-"
                + jobName
                + "-job'\n"
                + "}\n";
    }
}
