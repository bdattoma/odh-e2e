/*
 * Copyright Skodjob authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.odh.test.e2e.upgrade;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.odh.test.Environment;
import io.odh.test.OdhAnnotationsLabels;
import io.odh.test.TestSuite;
import io.odh.test.e2e.Abstract;
import io.odh.test.framework.listeners.OdhResourceCleaner;
import io.odh.test.framework.listeners.ResourceManagerDeleteHandler;
import io.odh.test.framework.manager.ResourceManager;
import io.odh.test.framework.manager.resources.NotebookResource;
import io.odh.test.utils.DscUtils;
import io.opendatahub.datasciencecluster.v1.DataScienceCluster;
import io.opendatahub.datasciencecluster.v1.DataScienceClusterBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.ComponentsBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Codeflare;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.CodeflareBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Dashboard;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.DashboardBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Datasciencepipelines;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.DatasciencepipelinesBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Kserve;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.KserveBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Modelmeshserving;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.ModelmeshservingBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Ray;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.RayBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Trustyai;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.TrustyaiBuilder;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.Workbenches;
import io.opendatahub.datasciencecluster.v1.datascienceclusterspec.components.WorkbenchesBuilder;
import io.opendatahub.dscinitialization.v1.DSCInitialization;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kubeflow.v1.Notebook;
import org.kubeflow.v1.NotebookBuilder;
import org.kubeflow.v1.notebookspec.template.spec.containers.EnvBuilder;

import java.io.IOException;

@Tag(TestSuite.UPGRADE)
@ExtendWith(OdhResourceCleaner.class)
@ExtendWith(ResourceManagerDeleteHandler.class)
public abstract class UpgradeAbstract extends Abstract {

    protected void deployDsc(String name) {
        DSCInitialization dsci = DscUtils.getBasicDSCI();

        // Deploy DSC
        DataScienceCluster dsc = new DataScienceClusterBuilder()
            .withNewMetadata()
            .withName(name)
            .endMetadata()
            .withNewSpec()
            .withComponents(
                new ComponentsBuilder()
                    .withWorkbenches(
                        new WorkbenchesBuilder().withManagementState(Workbenches.ManagementState.MANAGED).build()
                    )
                    .withDashboard(
                        new DashboardBuilder().withManagementState(Dashboard.ManagementState.MANAGED).build()
                    )
                    .withKserve(
                        new KserveBuilder().withManagementState(Kserve.ManagementState.REMOVED).build()
                    )
                    .withCodeflare(
                        new CodeflareBuilder().withManagementState(Codeflare.ManagementState.MANAGED).build()
                    )
                    .withDatasciencepipelines(
                        new DatasciencepipelinesBuilder().withManagementState(Datasciencepipelines.ManagementState.MANAGED).build()
                    )
                    .withModelmeshserving(
                        new ModelmeshservingBuilder().withManagementState(Modelmeshserving.ManagementState.MANAGED).build()
                    )
                    .withRay(
                        new RayBuilder().withManagementState(Ray.ManagementState.MANAGED).build()
                    )
                    .withTrustyai(
                        new TrustyaiBuilder().withManagementState(Trustyai.ManagementState.MANAGED).build()
                    )
                    .build())
            .endSpec()
            .build();
        // Deploy DSC
        ResourceManager.getInstance().createResourceWithWait(dsci);
        ResourceManager.getInstance().createResourceWithWait(dsc);
    }
    public void deployNotebook(String namespace, String name) throws IOException {
        Namespace ns = new NamespaceBuilder()
                .withNewMetadata()
                .withName(namespace)
                .addToLabels(OdhAnnotationsLabels.LABEL_DASHBOARD, "true")
                .addToAnnotations(OdhAnnotationsLabels.ANNO_SERVICE_MESH, "false")
                .endMetadata()
                .build();
        ResourceManager.getInstance().createResourceWithoutWait(ns);

        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .addToLabels(OdhAnnotationsLabels.LABEL_DASHBOARD, "true")
                .endMetadata()
                .withNewSpec()
                .addToAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity("10Gi"))
                .endResources()
                .withVolumeMode("Filesystem")
                .endSpec()
                .build();
        ResourceManager.getInstance().createResourceWithoutWait(pvc);

        String notebookImage = NotebookResource.getNotebookImage(NotebookResource.PYTORCH_IMAGE, NotebookResource.PYTORCH_2023_2_TAG);
        Notebook notebook = new NotebookBuilder(NotebookResource.loadDefaultNotebook(namespace, name, notebookImage)).build();
        if (!Environment.PRODUCT.equals(Environment.PRODUCT_DEFAULT)) {
            notebook = new NotebookBuilder(NotebookResource.loadDefaultNotebook(namespace, name, notebookImage))
                    .editSpec()
                    .editNotebookspecTemplate()
                    .editOrNewSpec()
                    .editContainer(0)
                    .withImage("image-registry.openshift-image-registry.svc:5000/redhat-ods-applications/pytorch:2023.2")
                    .addToEnv(new EnvBuilder().withName("JUPYTER_IMAGE").withValue("image-registry.openshift-image-registry.svc:5000/redhat-ods-applications/pytorch:2023.2").build())
                    .endSpecContainer()
                    .endTemplateSpec()
                    .endNotebookspecTemplate()
                    .endSpec()
                    .build();
        }

        ResourceManager.getInstance().createResourceWithoutWait(notebook);
    }
}
