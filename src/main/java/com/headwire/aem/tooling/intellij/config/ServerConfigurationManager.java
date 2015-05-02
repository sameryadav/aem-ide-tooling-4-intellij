package com.headwire.aem.tooling.intellij.config;

import com.headwire.aem.tooling.intellij.util.Util;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by schaefa on 3/19/15.
 */
@State(
    name = ServerConfiguration.COMPONENT_NAME,
    storages = {
        @Storage(id = "serverConfigurations", file = "$PROJECT_FILE$")
    }
)
public class ServerConfigurationManager
        implements PersistentStateComponent<Element>, ProjectComponent {

    private static final Logger LOGGER = Logger.getInstance(ServerConfigurationManager.class);

    public static ServerConfigurationManager getInstance(final Project project) {
        return ServiceManager.getService(project, ServerConfigurationManager.class);
    }

    private ConfigurationListener configurationListener;
    private List<ServerConfiguration> serverConfigurationList = new ArrayList<ServerConfiguration>();
    private Project project;

    public ServerConfigurationManager(final Project project) {
        this.project = project;
    }

    public ServerConfiguration[] getServerConfigurations() {
        return serverConfigurationList.toArray(new ServerConfiguration[] {});
    }

    public int serverConfigurationSize() {
        return serverConfigurationList.size();
    }

    public void addServerConfiguration(ServerConfiguration serverConfiguration) {
        serverConfigurationList.add(serverConfiguration);
        configurationListener.configurationLoaded();
    }

    public void removeServerConfiguration(ServerConfiguration serverConfiguration) {
        serverConfigurationList.remove(serverConfiguration);
        configurationListener.configurationLoaded();
    }

    public void updateServerConfiguration(ServerConfiguration serverConfiguration) {
//        serverConfigurationList.remove(serverConfiguration);
        configurationListener.configurationLoaded();
    }

    private volatile Boolean myIsInitialized = null;

    public boolean isInitialized() {
        final Boolean initialized = myIsInitialized;
        return initialized == null || initialized.booleanValue();
    }

    // ------ Project Component

    @Override
    public void projectOpened() {
    }

    @Override
    public void projectClosed() {
    }

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "Server Configuration Manager";
    }

    // -------------------- state persistence

    public Element getState() {
        LOGGER.debug("SCM.getState(), start");
        Element root = new Element("state");
        int i = 0;
        for(ServerConfiguration serverConfiguration: serverConfigurationList) {
            Element childNode = new Element("ssc-" + i);
            childNode.setAttribute("name", serverConfiguration.getName());
            childNode.setAttribute("host", serverConfiguration.getHost());
            childNode.setAttribute("description", serverConfiguration.getDescription());
            childNode.setAttribute("connectionPort", serverConfiguration.getConnectionPort() + "");
            childNode.setAttribute("connectionDebugPort", serverConfiguration.getConnectionDebugPort() + "");
            childNode.setAttribute("userName", serverConfiguration.getUserName());
            //AS TODO: Can we store that in an encrypted form?
            childNode.setAttribute("password", new String(serverConfiguration.getPassword()));
            childNode.setAttribute("contextPath", serverConfiguration.getContextPath());
            childNode.setAttribute("startConnectionTimeout", serverConfiguration.getStartConnectionTimeoutInSeconds() + "");
            childNode.setAttribute("stopConnectionTimeout", serverConfiguration.getStopConnectionTimeoutInSeconds() + "");
            childNode.setAttribute("publishType", serverConfiguration.getPublishType() + "");
            childNode.setAttribute("installationType", serverConfiguration.getInstallationType() + "");
            root.addContent(childNode);
        }
        LOGGER.debug("SCM.getState(), end -> returns: " + root);
        return root;
    }

    public void loadState(final Element state) {
        LOGGER.debug("SCM.loadState(), start");
        myIsInitialized = Boolean.FALSE;
        List<Element> elementList = state.getChildren();
        serverConfigurationList.clear();
        for(Element child: elementList) {
            ServerConfiguration serverConfiguration = new ServerConfiguration();
            serverConfiguration.setName(child.getAttributeValue("name"));
            serverConfiguration.setHost(child.getAttributeValue("host"));
            serverConfiguration.setDescription(child.getAttributeValue("description"));
            serverConfiguration.setConnectionPort(Util.convertToInt(child.getAttributeValue("connectionPort"), 0));
            serverConfiguration.setConnectionDebugPort(Util.convertToInt(child.getAttributeValue("connectionDebugPort"), 0));
            serverConfiguration.setUserName(child.getAttributeValue("userName"));
            serverConfiguration.setPassword(child.getAttributeValue("password").toCharArray());
            serverConfiguration.setContextPath(child.getAttributeValue("contextPath"));
            serverConfiguration.setStartConnectionTimeoutInSeconds(Util.convertToInt(child.getAttributeValue("startConnectionTimeout"), -1));
            serverConfiguration.setStopConnectionTimeoutInSeconds(Util.convertToInt(child.getAttributeValue("stopConnectionTimeout"), -1));
            serverConfiguration.setPublishType(Util.convertToEnum(child.getAttributeValue("publishType"), ServerConfiguration.DEFAULT_PUBLISH_TYPE));
            serverConfiguration.setInstallationType(Util.convertToEnum(child.getAttributeValue("installationType"), ServerConfiguration.DEFAULT_INSTALL_TYPE));
            LOGGER.debug("SCM.loadState(), add Server Configuration: " + serverConfiguration);
            serverConfigurationList.add(serverConfiguration);
        }
        myIsInitialized = Boolean.TRUE;
        final String title = "Loading Server Configurations";
        queueLater(
            new Task.Backgroundable(project, title, false) {
                public void run(@NotNull final ProgressIndicator indicator) {
                    if (getProject().isDisposed()) {
                        return;
                    }
                    indicator.setIndeterminate(true);
                    indicator.pushState();
                    try {
                        indicator.setText(title);
                        ApplicationManager.getApplication().runReadAction(
                            new Runnable() {
                                public void run() {
                                    if(configurationListener != null) {
                                        configurationListener.configurationLoaded();
                                    }
                                }
                            }
                        );
                    } finally {

                    }
                }
            }
        );
    }

    public void addConfigurationListener(ConfigurationListener myConfigurationListener) {
        configurationListener = myConfigurationListener;
    }

    private static void queueLater(final Task task) {
        final Application app = ApplicationManager.getApplication();
        if (app.isDispatchThread()) {
            task.queue();
        } else {
            app.invokeLater(new Runnable() {
                public void run() {
                    task.queue();
                }
            });
        }
    }

}