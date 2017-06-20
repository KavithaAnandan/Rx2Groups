/*
 * Copyright (C) 2016 Airbnb, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.airbnb.rxgroups;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.reactivestreams.Subscription;

import java.util.UUID;

import javax.annotation.Nullable;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.ResourceObserver;


/**
 * TODO
 */
@SuppressWarnings("WeakerAccess")
public class GroupLifecycleManager {
    private static final String KEY_STATE = "KEY_GROUPLIFECYCLEMANAGER_STATE";

    private final CompositeDisposable pendingSubscriptions = new CompositeDisposable();
    private final LifecycleResubscription resubscription;
    private final ObservableManager observableManager;
    private final ObservableGroup group;
    private boolean hasSavedState;

    private GroupLifecycleManager(ObservableManager observableManager, ObservableGroup group,
                                  LifecycleResubscription resubscription) {
        this.observableManager = observableManager;
        this.group = group;
        this.resubscription = resubscription;
    }

    /**
     * Call this method from your Activity or Fragment's onCreate method
     */
    public static GroupLifecycleManager onCreate(ObservableManager observableManager,
                                                 LifecycleResubscription resubscription) {
        return onCreate(observableManager, resubscription, null, null);
    }

    /**
     * Call this method from your Activity or Fragment's onCreate method
     */
    public static GroupLifecycleManager onCreate(ObservableManager observableManager,
                                                 @Nullable Bundle savedState, @Nullable Object target) {
        return onCreate(observableManager, new LifecycleResubscription(), savedState,
                target);
    }

    /**
     * Call this method from your Activity or Fragment's onCreate method
     */
    public static GroupLifecycleManager onCreate(ObservableManager observableManager,
                                                 LifecycleResubscription resubscription, @Nullable Bundle savedState,
                                                 @Nullable Object target) {
        // Only need to resubscribe if restoring from saved state
        boolean shouldResubscribe = target != null && savedState != null;
        ObservableGroup group;

        if (savedState != null) {
            State state = savedState.getParcelable(KEY_STATE);

            Preconditions.checkState(state != null, "Must call onSaveInstanceState() first");

            // First check the instance ID before restoring state. If it's not the same instance,
            // then we have to create a new group since the previous one is already destroyed.
            // Android can sometimes reuse the same instance after saving state and we can't reliably
            // determine when that happens. This is a workaround for that behavior.
            if (state.managerId != observableManager.id()) {
                group = observableManager.newGroup();
            } else {
                group = observableManager.getGroup(state.groupId);
            }
        } else {
            group = observableManager.newGroup();
        }

        group.lock();

        GroupLifecycleManager manager = new GroupLifecycleManager(observableManager, group,
                resubscription);

        if (shouldResubscribe) {
            manager.subscribe(target);
        }

        return manager;
    }

    /**
     * @return the {@link ObservableGroup} associated to this instance
     */
    public ObservableGroup group() {
        return group;
    }

    /**
     * TODO
     */
    public <T> ObservableTransformer<? super T, T> transform(String tag) {
        return group.transform(tag);
    }

    /**
     * Returns whether the provided {@link Class} exists for the {@link ObservableGroup}.
     * Observables will only be removed from their respective groups once {@link
     * Observer#onComplete()} has been called.
     */
    public boolean hasObservable(String tag) {
        return group.hasObservable(tag);
    }

    /**
     * Subscribe all Observer fields on the target that are annotated with {@link AutoResubscribe}
     * and that have their corresponding Observable in flight.
     */
    public void subscribe(Object target) {
        Disposable subscription = resubscription.observers(target)
                .filter(new Predicate<LifecycleResubscription.ObserverInfo>() {
                    @Override
                    public boolean test(@NonNull LifecycleResubscription.ObserverInfo observerInfo) {
                        return hasObservable(observerInfo.tag);
                    }
                }).subscribe(new Consumer<LifecycleResubscription.ObserverInfo>() {
                    @Override
                    public void accept(@NonNull LifecycleResubscription.ObserverInfo observerInfo) throws Exception {
                        resubscribe(observerInfo.tag, observerInfo.observer);
                    }
                });
        pendingSubscriptions.add(subscription);
    }

    /**
     * Resubscribes an existing {@link Class} If the Observable has already emitted events,
     * they will be immediately delivered if the group is
     * unlocked. Any previously subscribed Observers will be unsubscribed before the new one.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void resubscribe(String tag, ResourceObserver<?> observer) {
        Observable observable = group.observable(tag);
        ManagedObservable managedObservable = (ManagedObservable) group.subscription(tag);
        observable.compose(new GroupResubscriptionTransformer<>(group, managedObservable, observer));
        observable.subscribe(observer);
    }

    /**
     * Cancel the given request if it is running, so that no future request callbacks are received.
     * If the request is not running then nothing
     * happens.
     */
    public void cancelAndRemove(String tag) {
        group.cancelAndRemove(tag);
    }

    private void onDestroy(boolean isFinishing) {
        pendingSubscriptions.clear();

        if (isFinishing) {
            observableManager.destroy(group);
        } else {
            group.unsubscribe();
        }
    }

    /**
     * Call this method from your Activity or Fragment's onDestroy method
     */
    public void onDestroy(@Nullable Activity activity) {
        // We need to track whether the current Activity is finishing or not in order to decide if we
        // should destroy the ObservableGroup. If the Activity is not finishing, then we should not
        // destroy it, since we know that we're probably restoring state at some point and reattaching
        // to the existing ObservableGroup. If saveState() was not called, then it's likely that we're
        // being destroyed and are not ever coming back. However, this isn't perfect, especially
        // when using fragments in a ViewPager. We might want to allow users to explicitly destroy it
        // instead, in order to mitigate this issue.
        // Also in some situations it seems like an Activity can be recreated even though its
        // isFinishing() property is set to true. For this reason, we must also check
        // isChangingConfigurations() to make sure it's safe to destroy the group.
        onDestroy(!hasSavedState
                || (activity != null && activity.isFinishing() && !activity.isChangingConfigurations()));
    }

    /**
     * Call this method from your Activity or Fragment's onDestroy method
     */
    public void onDestroy(Fragment fragment) {
        onDestroy(fragment.getActivity());
    }

    /**
     * Call this method from your Activity or Fragment's onResume method
     */
    public void onResume() {
        hasSavedState = false;
        unlock();
    }

    /**
     * Call this method from your Activity or Fragment's onPause method
     */
    public void onPause() {
        lock();
    }

    private void lock() {
        group.lock();
    }

    private void unlock() {
        group.unlock();
    }

    /**
     * Call this method from your Activity or Fragment's onSaveInstanceState method
     */
    public void onSaveInstanceState(Bundle outState) {
        hasSavedState = true;
        outState.putParcelable(KEY_STATE, new State(observableManager.id(), group.id()));
    }

    static class State implements Parcelable {
        final UUID managerId;
        final long groupId;

        State(UUID managerId, long groupId) {
            this.managerId = managerId;
            this.groupId = groupId;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeSerializable(managerId);
            dest.writeLong(groupId);
        }

        public static final Parcelable.Creator<State> CREATOR = new Parcelable.Creator<State>() {
            @Override
            public State[] newArray(int size) {
                return new State[size];
            }

            @Override
            public State createFromParcel(Parcel source) {
                return new State((UUID) source.readSerializable(), source.readLong());
            }
        };
    }
}
