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



import io.reactivex.BackpressureStrategy;
import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.observers.ResourceObserver;

/**
 * Transforms an existing {@link Observable} by returning a new {@link Observable} that is
 * automatically added to the provided {@link ObservableGroup} with the specified {@code tag} when
 * subscribed to.
 */
class GroupSubscriptionTransformer<T> implements ObservableTransformer<T, T> {
    private final ObservableGroup group;
    private final String tag;

    GroupSubscriptionTransformer(ObservableGroup group, String tag) {
        this.group = group;
        this.tag = tag;
    }

    @Override
    public ObservableSource<T> apply(@NonNull final Observable<T> observable) {

        return Observable.create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(@NonNull final ObservableEmitter<T> emitter) throws Exception {
                group.add(tag, observable, new ResourceObserver<T>() {
                    @Override
                    public void onNext(@NonNull T t) {
                        emitter.onNext(t);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        emitter.onError(e);
                    }

                    @Override
                    public void onComplete() {
                        emitter.onComplete();
                    }
                });
            }
        });


//      return Observable.create(new ObservableOnSubscribe<T>() {
//          @Override
//          public void subscribe(@NonNull final ObservableEmitter<T> emitter) throws Exception {
//              group.add(tag, observable, new Observer<T>() {
//                  @Override
//                  public void onSubscribe(@NonNull Disposable d) {
//
//                  }
//
//                  @Override
//                  public void onNext(@NonNull T t) {
//                      emitter.onNext(t);
//                  }
//
//                  @Override
//                  public void onError(@NonNull Throwable e) {
//                      emitter.onError(e);
//                  }
//
//                  @Override
//                  public void onComplete() {
//
//                  }
//              });
//          }
//      });

        //Rx1 code
//    return Observable.fromEmitter(new Action1<Emitter<T>>() {
//      @Override public void call(final Emitter<T> emitter) {
//        group.add(tag, observable, new Observer<T>() {
//
//          @Override public void onError(Throwable e) {
//            emitter.onError(e);
//          }
//
//          @Override
//          public void onComplete() {
//            emitter.onComplete();
//          }
//
//          @Override
//          public void onSubscribe(@NonNull Disposable d) {
//
//          }
//
//          @Override public void onNext(T t) {
//            emitter.onNext(t);
//          }
//        });
//      }
//    }, BackpressureStrategy.BUFFER);
    }
}
