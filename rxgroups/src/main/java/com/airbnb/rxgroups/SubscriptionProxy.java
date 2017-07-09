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
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.subjects.ReplaySubject;

/**
 * This class is a middle man between an {@link Observable} and an {@link Observer}. Since Retrofit
 * cancels upon unsubscription, this allows us to unsubscribe without cancelling the underlying
 * OkHttp call by using a proxy {@link ReplaySubject}. This is especially useful during activities
 * onPause() -> onResume(), where we want to avoid updating the UI but we still don't want to cancel
 * the request. This works like a lock/unlock events mechanism. It can be unlocked by calling
 * subscribe() again with the same Observable. Cancellation is usually more suited for lifecycle
 * events like Activity.onDestroy()
 */
final class SubscriptionProxy<T> {
    private final Observable<T> proxy;
    //private final Subscription upstreamSubscription;
    //private final CompositeSubscription subscriptionList;
    private final CompositeDisposable disposableList;
    //private Subscription subscription;
    private Disposable disposable;
    private boolean isUpstreamSubscriptionAlive;


    private SubscriptionProxy(Observable<T> upstreamObservable, Action onTerminate) {
        ReplaySubject<T> replaySubject = ReplaySubject.create();
        upstreamObservable.subscribe(replaySubject);
        proxy = replaySubject.doOnTerminate(onTerminate);
        disposableList = new CompositeDisposable();
        isUpstreamSubscriptionAlive = true;
    }


    static <T> SubscriptionProxy<T> create(Observable<T> observable, Action onTerminate) {
        return new SubscriptionProxy<>(observable, onTerminate);
    }

    static <T> SubscriptionProxy<T> create(Observable<T> observable) {
        return create(observable, new Action() {
            @Override
            public void run() throws Exception {

            }
        });
    }

    void subscribe(Observer<? super T> observer) {
        subscribe(proxy, observer);
    }

    void subscribe(Observable<T> observable, final Observer<? super T> observer) {
        unsubscribe();
        observable.subscribe(new Observer<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                disposable = d;
                disposableList.add(disposable);
            }

            @Override
            public void onNext(T t) {
                observer.onNext(t);
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        });
    }

    void cancel() {
        disposableList.dispose();
        isUpstreamSubscriptionAlive = false;
    }

    void unsubscribe() {
        if (disposable != null) {
            disposableList.remove(disposable);
        }
    }

    boolean isUnsubscribed() {
        return disposable != null && disposable.isDisposed();
    }

    boolean isCancelled() {
        return isUnsubscribed() && !isUpstreamSubscriptionAlive;
    }


    Observable<T> observable() {
        return proxy;
    }
}
