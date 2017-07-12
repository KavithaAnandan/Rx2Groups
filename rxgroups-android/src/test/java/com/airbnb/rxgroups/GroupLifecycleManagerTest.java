package com.airbnb.rxgroups;

import android.app.Activity;
import android.os.Build;

import com.airbnb.rxgroups.LifecycleResubscription.ObserverInfo;
import com.airbnb.rxgroups.android.BuildConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;


import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(sdk = Build.VERSION_CODES.LOLLIPOP, constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class GroupLifecycleManagerTest extends BaseTest {
    private final LifecycleResubscription resubscription = mock(LifecycleResubscription.class);
    private final TestScheduler scheduler = new TestScheduler();
    private final PublishSubject<Object> testSubject = PublishSubject.create();
    private final TestObserver<String> testObserver = new TestObserver<>();
    private final String tag = "test";
    private final ObserverInfo observerInfo = new ObserverInfo(tag, testObserver);
    private final ObservableManager observableManager = mock(ObservableManager.class);
    private final ObservableGroup group = mock(ObservableGroup.class);
    private final Object target = new Object();

    @Before
    public void setUp() {
        testSubject.observeOn(scheduler);
    }

    @Test
    public void testSubscribe() {
        when(observableManager.newGroup()).thenReturn(group);
        when(group.hasObservable(tag)).thenReturn(true);
        when(group.observable(tag)).thenReturn(testSubject);
        when(resubscription.observers(target)).thenReturn(Observable.just(observerInfo));

        GroupLifecycleManager.onCreate(observableManager, resubscription).subscribe(target);

        assertThat(testSubject.hasObservers()).isTrue();

        testSubject.onNext("hello");
        testSubject.onComplete();
        scheduler.triggerActions();

        testObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue("hello");
    }

    @Test
    public void testSubscribeNoObservables() {
        when(observableManager.newGroup()).thenReturn(group);
        when(group.hasObservable(tag)).thenReturn(false);
        when(group.observable(tag)).thenReturn(testSubject);
        when(resubscription.observers(target)).thenReturn(Observable.just(observerInfo));

        GroupLifecycleManager.onCreate(observableManager, resubscription).subscribe(target);

        assertThat(testSubject.hasObservers()).isFalse();

        testSubject.onNext("hello");
        testSubject.onComplete();
        scheduler.triggerActions();

        testObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testObserver.assertNotComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void testSubscribeNoObservers() {
        when(observableManager.newGroup()).thenReturn(group);
        when(group.hasObservable(tag)).thenReturn(true);
        when(group.observable(tag)).thenReturn(testSubject);
        when(resubscription.observers(target)).thenReturn(Observable.<ObserverInfo>empty());

        GroupLifecycleManager.onCreate(observableManager, resubscription).subscribe(target);

        assertThat(testSubject.hasObservers()).isFalse();

        testSubject.onNext("hello");
        testSubject.onComplete();
        scheduler.triggerActions();
    }

    @Test
    public void testDestroyFinishingActivity() {
        when(observableManager.newGroup()).thenReturn(group);
        when(group.hasObservable(tag)).thenReturn(true);
        when(group.observable(tag)).thenReturn(testSubject);
        when(resubscription.observers(target)).thenReturn(Observable.just(observerInfo));

        GroupLifecycleManager lifecycleManager =
                GroupLifecycleManager.onCreate(observableManager, resubscription);
        lifecycleManager.subscribe(target);

        Activity activity = mock(Activity.class);
        when(activity.isFinishing()).thenReturn(true);

        lifecycleManager.onDestroy(activity);

        verify(observableManager).destroy(group);
    }
}