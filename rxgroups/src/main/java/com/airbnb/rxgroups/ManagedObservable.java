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


import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.functions.Action;
import io.reactivex.observers.ResourceObserver;

/**
 * A wrapper for a {@link SubscriptionProxy} for use with the {@link ObservableGroup} to monitor a
 * subscription state by tag.
 */
class ManagedObservable<T> implements RequestSubscription {
    private final String tag;
    private final SubscriptionProxy<T> proxy;
    private boolean locked = true;
    private Observable<T> observable;
    private ResourceObserver<? super T> observer;

    ManagedObservable(String tag, Observable<T> observable, ResourceObserver<? super T> observer,
                      Action onTerminate) {
        this.tag = tag;
        this.observer = observer;
        proxy = SubscriptionProxy.create(observable, onTerminate);
        this.observable = proxy.observable();
    }

    @Override
    public boolean isCancelled() {
        return proxy.isCancelled();
    }

    @Override
    public void cancel() {
        proxy.cancel();
        observer = null;
    }

    void lock() {
        locked = true;
        proxy.unsubscribe();
    }

    @Override
    public void unsubscribe() {
        proxy.unsubscribe();
        observer = null;
    }

    @Override
    public boolean isUnsubscribed() {
        return proxy.isUnsubscribed();
    }

    void unlock() {
        locked = false;

        if (observer != null) {
            proxy.subscribe(observable, observer);
        }
    }

    Observable<T> observable() {
        return proxy.observable();
    }

    void resubscribe(Observable<T> observable, Observer<? super T> observer) {
        this.observable = observable;
        this.observer = Preconditions.checkNotNull(observer);

        if (!locked) {
            proxy.subscribe(observable, observer);
        }
    }

    String tag() {
        return tag;
    }

    @Override
    public String toString() {
        return "ManagedObservable{"
                + "tag='" + tag + '\''
                + '}';
    }
}
