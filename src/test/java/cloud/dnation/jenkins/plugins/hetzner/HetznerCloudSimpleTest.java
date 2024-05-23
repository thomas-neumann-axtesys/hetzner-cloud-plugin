/*
 *     Copyright 2021 https://dnation.cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cloud.dnation.jenkins.plugins.hetzner;

import cloud.dnation.jenkins.plugins.hetzner.launcher.AbstractHetznerSshConnector;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
public class HetznerCloudSimpleTest {
    private HetznerCloudResourceManager rsrcMgr;

    MockedStatic<Jenkins> jenkinsMock;
    MockedStatic<HetznerCloudResourceManager> hetznerCloudResourceManagerMockedStatic;
    @Before
    public void setupBefore() {
        jenkinsMock = Mockito.mockStatic(Jenkins.class);
        hetznerCloudResourceManagerMockedStatic = Mockito.mockStatic(HetznerCloudResourceManager.class);
        //PowerMockito.mockStatic(Jenkins.class, HetznerCloudResourceManager.class);
        rsrcMgr = mock(HetznerCloudResourceManager.class);
        Mockito.when(HetznerCloudResourceManager.create(anyString())).thenReturn(rsrcMgr);

        Jenkins jenkins = mock(Jenkins.class);
        doAnswer((Answer<LabelAtom>) invocationOnMock -> new LabelAtom(invocationOnMock.getArgument(0)))
                .when(jenkins).getLabelAtom(anyString());
        Mockito.when(Jenkins.get()).thenReturn(jenkins);
    }

    @After
    public void cleanMock() {
        jenkinsMock.close();
        hetznerCloudResourceManagerMockedStatic.close();
    }

    @Test
    public void testCanProvision() throws IOException {

        HetznerServerTemplate template1 = new HetznerServerTemplate("template-1", "java",
                "name=img1", "nbg1", "cx21");
        final AbstractHetznerSshConnector connector = mock(AbstractHetznerSshConnector.class);
        template1.setConnector(connector);

        final HetznerCloud cloud = new HetznerCloud("hcloud-01", "mock-credentials", "10",
                Lists.newArrayList(template1));
        Cloud.CloudState cloudState = new Cloud.CloudState(new LabelAtom("java"), 1);
        assertTrue(cloud.canProvision(cloudState));

        final Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(cloudState, 1);
        final NodeProvisioner.PlannedNode node = Iterables.getOnlyElement(plannedNodes);
        await().atMost(30, TimeUnit.SECONDS).until(node.future::isDone);
        verify(connector, times(1)).createLauncher();
        verify(rsrcMgr, times(1)).fetchAllServers(anyString());

        Cloud.CloudState cloudState2 = new Cloud.CloudState(new LabelAtom("unknown"), 1);
        assertFalse(cloud.canProvision(cloudState2));
    }

    @Test
    public void testCannotProvisionInExclusiveMode() {
        HetznerServerTemplate tmpl1 = new HetznerServerTemplate("tmpl1", "label1", "img1", "fsn1", "cx31");
        tmpl1.setMode(Node.Mode.EXCLUSIVE);
        final HetznerCloud cloud = new HetznerCloud("hcloud-01", "mock-credentials", "10",
                Lists.newArrayList(tmpl1)
        );
        Cloud.CloudState cloudState = new Cloud.CloudState(new LabelAtom("java"), 1);
        assertFalse(cloud.canProvision(cloudState));
    }

    @Test
    public void testCanProvisionInNormalMode() {
        HetznerServerTemplate tmpl1 = new HetznerServerTemplate("tmpl1", null, "img1", "fsn1", "cx31");
        tmpl1.setMode(Node.Mode.NORMAL);
        final HetznerCloud cloud = new HetznerCloud("hcloud-01", "mock-credentials", "10",
                Lists.newArrayList(tmpl1)
        );
        Cloud.CloudState cloudState = new Cloud.CloudState(new LabelAtom("java"), 1);
        assertTrue(cloud.canProvision(cloudState));
    }

    //see https://github.com/jenkinsci/hetzner-cloud-plugin/issues/15
    @Test
    public void testCanProvisionNullJobLabel() {
        HetznerServerTemplate tmpl1 = new HetznerServerTemplate("tmpl1", null, "img1", "fsn1", "cx31");
        tmpl1.setMode(Node.Mode.NORMAL);
        HetznerServerTemplate tmpl2 = new HetznerServerTemplate("tmpl1", "label2,label3", "img1", "fsn1", "cx31");
        tmpl2.setMode(Node.Mode.EXCLUSIVE);
        final HetznerCloud cloud = new HetznerCloud("hcloud-01", "mock-credentials", "10",
                Lists.newArrayList(tmpl1, tmpl2)
        );
        Cloud.CloudState cloudState = new Cloud.CloudState(null, 1);
        assertTrue(cloud.canProvision(cloudState));
        Cloud.CloudState cloudState2 = new Cloud.CloudState(new LabelAtom("label3"), 1);
        assertTrue(cloud.canProvision(cloudState2));
    }
}
