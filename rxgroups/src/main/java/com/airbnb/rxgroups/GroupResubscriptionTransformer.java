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


import org.reactivestreams.Subscriber;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.annotations.NonNull;
import io.reactivex.observers.ResourceObserver;

class GroupResubscriptionTransformer<T> implements ObservableTransformer<T, T> {
    private final ObservableGroup group;
    private final ManagedObservable<T> managedObservable;
    private final ResourceObserver<T> resourceObserver;


    GroupResubscriptionTransformer(
            ObservableGroup group, ManagedObservable<T> managedObservable, ResourceObserver<T> resourceObserver) {
        this.group = group;
        this.managedObservable = managedObservable;
        this.resourceObserver = resourceObserver;
    }

    @Override
    public ObservableSource<T> apply(@NonNull final Observable<T> observable) {
        return Observable.<T>create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<T> e) throws Exception {
                group.resubscribe(managedObservable, observable, resourceObserver);

            }
        });
        // changed here


//    return Observable.<T>create(new Observable.OnSubscribe<T>() {
//      @Override public void call(Subscriber<? super T> subscriber) {
//        group.resubscribe(managedObservable, observable, subscriber);
//      }
//    });
    }
}
