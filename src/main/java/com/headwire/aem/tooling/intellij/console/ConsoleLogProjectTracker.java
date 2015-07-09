package com.headwire.aem.tooling.intellij.console;

import com.headwire.aem.tooling.intellij.config.ServerConfiguration;
import com.headwire.aem.tooling.intellij.explorer.ServerTreeSelectionHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.headwire.aem.tooling.intellij.console.ConsoleLog.DEFAULT_CATEGORY;
import static com.headwire.aem.tooling.intellij.console.ConsoleLog.LOG_REQUESTOR;

/**
 * Created by schaefa on 7/3/15.
 */
public class ConsoleLogProjectTracker
    extends AbstractProjectComponent
{
    private final Map<String, ConsoleLogConsole> myCategoryMap = ContainerUtil.newConcurrentMap();
    private final List<Notification> myInitial = ContainerUtil.createLockFreeCopyOnWriteList();
    private final ConsoleLogModel myProjectModel;

    public ConsoleLogProjectTracker(@NotNull final Project project) {
        super(project);

        myProjectModel = new ConsoleLogModel(project, project);

        for(Notification notification : ConsoleLog.getApplicationComponent().getModel().takeNotifications()) {
            printNotification(notification);
        }

        project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new NotificationsAdapter() {
            @Override
            public void notify(@NotNull Notification notification) {
                printNotification(notification);
            }
        });
    }

    public void initDefaultContent() {
        createNewContent(DEFAULT_CATEGORY);

        for(Notification notification : myInitial) {
            doPrintNotification(notification, ObjectUtils.assertNotNull(getConsole(notification)));
        }
        myInitial.clear();
    }

    public ConsoleLogModel getMyProjectModel() {
        return myProjectModel;
    }

    //        @Override
//        public void projectOpened() {
//        }
//
    @Override
    public void projectClosed() {
        ConsoleLog.getApplicationComponent().getModel().setStatusMessage(null, 0);
        StatusBar.Info.set("", null, LOG_REQUESTOR);
    }

    protected void printNotification(Notification notification) {
        ServerTreeSelectionHandler selectionHandler = ServiceManager.getService(myProject, ServerTreeSelectionHandler.class);
        if(selectionHandler != null) {
            ServerConfiguration serverConfiguration = selectionHandler.getCurrentConfiguration();
            ServerConfiguration.LogFilter logFilter = serverConfiguration != null ? serverConfiguration.getLogFilter() : ServerConfiguration.LogFilter.info;
            switch (logFilter) {
                case debug:
                    break;
                case info:
                    if (notification instanceof DebugNotification) {
                        return;
                    }
                    break;
                case warning:
                    if (notification.getType() == NotificationType.INFORMATION) {
                        return;
                    }
                    break;
                case error:
                default:
                    if (notification.getType() != NotificationType.ERROR) {
                        return;
                    }
            }
        }
//            if(!NotificationsConfigurationImpl.getSettings(notification.getGroupId()).isShouldLog()) {
//                return;
//            }
        myProjectModel.addNotification(notification);

        ConsoleLogConsole console = getConsole(notification);
        if(console == null) {
            myInitial.add(notification);
        } else {
            doPrintNotification(notification, console);
        }
    }

    private void doPrintNotification(@NotNull final Notification notification, @NotNull final ConsoleLogConsole console) {
        StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
            @Override
            public void run() {
                if(!ShutDownTracker.isShutdownHookRunning() && !myProject.isDisposed()) {
                    ApplicationManager.getApplication().runReadAction(new Runnable() {
                        public void run() {
                            console.doPrintNotification(notification);
                        }
                    });
                }
            }
        });
    }

    @Nullable
    protected ConsoleLogConsole getConsole(Notification notification) {
        if(myCategoryMap.get(DEFAULT_CATEGORY) == null) {
            return null; // still not initialized
        }

        String name = ConsoleLog.getContentName(notification);
        ConsoleLogConsole console = myCategoryMap.get(name);
        return console != null ? console : createNewContent(name);
    }

    @NotNull
    private ConsoleLogConsole createNewContent(String name) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        ConsoleLogConsole newConsole = new ConsoleLogConsole(myProjectModel);
//AS This does the same thing as the line commented out below
//AS TODO: This creates an endless loop
//            getProjectComponent(myProject).initDefaultContent();
        ConsoleLogToolWindowFactory.createContent(myProject, ConsoleLog.getLogWindow(myProject), newConsole, name);
        myCategoryMap.put(name, newConsole);

        return newConsole;
    }

}