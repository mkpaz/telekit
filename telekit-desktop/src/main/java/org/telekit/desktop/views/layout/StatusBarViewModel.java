package org.telekit.desktop.views.layout;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import org.telekit.base.desktop.mvvm.Command;
import org.telekit.base.desktop.mvvm.CommandBase;
import org.telekit.base.desktop.mvvm.ViewModel;
import org.telekit.base.di.Initializable;
import org.telekit.base.event.DefaultEventBus;
import org.telekit.base.event.Listener;
import org.telekit.base.domain.event.TaskProgressEvent;
import org.telekit.base.preferences.internal.ApplicationPreferences;
import org.telekit.base.preferences.internal.SecurityPreferences;
import org.telekit.base.preferences.internal.Vault;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static org.telekit.base.util.NumberUtils.round;

@Singleton
public class StatusBarViewModel implements Initializable, ViewModel {

    static final int VAULT_LOCKED = 0;
    static final int VAULT_UNLOCKED = 1;
    static final int VAULT_UNLOCK_FAILED = -1;

    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final int MB = 1024 * 1024;

    private final ApplicationPreferences preferences;
    private final Vault vault;

    @Inject
    public StatusBarViewModel(ApplicationPreferences preferences,
                              Vault vault) {
        this.preferences = preferences;
        this.vault = vault;
    }

    @Override
    public void initialize() {
        DefaultEventBus.getInstance().subscribe(TaskProgressEvent.class, this::toggleProgressIndicator);
        startMemoryUsageMonitoring();

        // unlock the vault (do not run from FX thread)
        Task<Void> vaultUnlockTask = new Task<>() {
            @Override
            protected Void call() {
                SecurityPreferences security = preferences.getSecurityPreferences();
                if (security.isAutoUnlock() && !vault.isUnlocked()) {
                    vault.unlock(security.getDerivedVaultPassword());
                }
                return null;
            }
        };
        vaultUnlockTask.setOnSucceeded(e -> {
            // also handles newly created vault
            if (vault.isUnlocked()) { vaultState.set(VAULT_UNLOCKED); }
        });
        vaultUnlockTask.setOnFailed(e -> vaultState.set(VAULT_UNLOCK_FAILED));
        vaultUnlockTask.run();
    }

    private void startMemoryUsageMonitoring() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                updateMemoryUsage();
            }
        }, 0, TimeUnit.MINUTES.toMillis(1));
    }

    private void updateMemoryUsage() {
        long total = RUNTIME.totalMemory();
        totalMemory.set(round((double) total / MB, 1));
        usedMemory.set(round(((double) total - RUNTIME.freeMemory()) / MB, 1));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Properties                                                            //
    ///////////////////////////////////////////////////////////////////////////

    //@formatter:off
    private final ReadOnlyIntegerWrapper vaultState = new ReadOnlyIntegerWrapper(this, "vaultState", VAULT_LOCKED);
    public ReadOnlyIntegerProperty vaultStateProperty() { return vaultState.getReadOnlyProperty(); }

    private final ObservableSet<String> activeTasks = FXCollections.observableSet();
    public ObservableSet<String> activeTasks() { return activeTasks; }

    private final ReadOnlyDoubleWrapper usedMemory = new ReadOnlyDoubleWrapper(this, "usedMemory");
    public ReadOnlyDoubleProperty usedMemoryProperty() { return usedMemory.getReadOnlyProperty(); }

    private final ReadOnlyDoubleWrapper totalMemory = new ReadOnlyDoubleWrapper(this, "totalMemory");
    public ReadOnlyDoubleProperty totalMemoryProperty() { return totalMemory.getReadOnlyProperty(); }
    //@formatter:on

    ///////////////////////////////////////////////////////////////////////////
    // Commands                                                              //
    ///////////////////////////////////////////////////////////////////////////

    private final Command runGcCommand = new CommandBase() {
        @Override
        protected void doExecute() {
            System.gc();
            updateMemoryUsage();
        }
    };

    public Command runGcCommand() { return runGcCommand; }

    ///////////////////////////////////////////////////////////////////////////
    // Event Bus                                                             //
    ///////////////////////////////////////////////////////////////////////////

    @Listener
    private synchronized void toggleProgressIndicator(TaskProgressEvent event) {
        if (event.isRunning()) {
            activeTasks.add(event.getTaskId());
        } else {
            activeTasks.remove(event.getTaskId());
        }
    }
}
