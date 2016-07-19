/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.support.v4.ShadowLocalBroadcastManager;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;

@SuppressWarnings("ResourceType")
public class AnalyticsTest extends BaseTestCase {

    Analytics analytics;
    ActivityMonitor.Listener activityMonitorListener;
    ActivityMonitor mockActivityMonitor;
    LocalBroadcastManager localBroadcastManager;
    ShadowApplication shadowApplication;


    @Before
    public void setup() {
        mockActivityMonitor = Mockito.mock(ActivityMonitor.class);
        ArgumentCaptor<ActivityMonitor.Listener> listenerCapture = ArgumentCaptor.forClass(ActivityMonitor.Listener.class);

        Mockito.doNothing().when(mockActivityMonitor).setListener(listenerCapture.capture());
        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        this.analytics = new Analytics(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, airshipConfigOptions, UAirship.ANDROID_PLATFORM, mockActivityMonitor);
        analytics.init();

        activityMonitorListener = listenerCapture.getValue();
        assertNotNull("Should set the listener on create", activityMonitorListener);

        shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        shadowApplication.clearStartedServices();

        localBroadcastManager = LocalBroadcastManager.getInstance(TestApplication.getApplication());
        TestApplication.getApplication().setAnalytics(analytics);
    }

    /**
     * Test that a session id is created when analytics is created
     */
    @Test
    public void testOnCreate() {
        assertNotNull("Session id should generate on create", analytics.getSessionId());
    }

    /**
     * Test that when the app goes into the foreground, a new
     * session id is created, a broadcast is sent for foreground, isForegorund
     * is set to true, and a foreground event is added to the event service.
     */
    @Test
    public void testOnForeground() {
        // Start analytics in the background
        activityMonitorListener.onBackground(0);

        assertFalse(analytics.isAppInForeground());

        // Grab the session id to compare it to a new session id
        String sessionId = analytics.getSessionId();

        activityMonitorListener.onForeground(0);

        // Verify that we generate a new session id
        assertNotNull(analytics.getSessionId());
        assertNotSame("A new session id should be generated on foreground", analytics.getSessionId(), sessionId);


        // Verify isAppInForeground is true
        assertTrue(analytics.isAppInForeground());

        // Verify we sent a broadcast intent for app foreground
        ShadowLocalBroadcastManager shadowLocalBroadcastManager = org.robolectric.shadows.support.v4.Shadows.shadowOf(localBroadcastManager);
        List<Intent> broadcasts = shadowLocalBroadcastManager.getSentBroadcastIntents();
        assertEquals("Should of sent a foreground local broadcast",
                broadcasts.get(broadcasts.size() - 1).getAction(), Analytics.ACTION_APP_FOREGROUND);
    }

    /**
     * Test that when the app goes into the background, the conversion send id is
     * cleared, a background event is sent to be added, and a broadcast for background
     * is sent.
     */
    @Test
    public void testOnBackground() {
        // Start analytics in the foreground
        activityMonitorListener.onForeground(0);
        shadowApplication.clearStartedServices();
        assertTrue(analytics.isAppInForeground());

        analytics.setConversionSendId("some-id");

        activityMonitorListener.onBackground(0);

        // Verify that we clear the conversion send id
        assertNull("App background should clear the conversion send id", analytics.getConversionSendId());

        // Verify that a app background event is sent to the service to be added
        Intent addEventIntent = shadowApplication.getNextStartedService();
        assertNotNull("Going into background should start the event service", addEventIntent);
        assertEquals("Should add an intent with action ADD", addEventIntent.getAction(), AnalyticsIntentHandler.ACTION_ADD);
        assertEquals("Should add an app background event", AppBackgroundEvent.TYPE,
                addEventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));

        // Verify isAppInForeground is false
        assertFalse(analytics.isAppInForeground());

        // Verify we sent a broadcast intent for app background
        ShadowLocalBroadcastManager shadowLocalBroadcastManager = org.robolectric.shadows.support.v4.Shadows.shadowOf(localBroadcastManager);
        List<Intent> broadcasts = shadowLocalBroadcastManager.getSentBroadcastIntents();
        assertEquals("Should of sent a background local broadcast",
                broadcasts.get(broadcasts.size() - 1).getAction(), Analytics.ACTION_APP_BACKGROUND);
    }

    /**
     * Test setting the conversion conversion send id
     */
    @Test
    public void testSetConversionPushId() {
        analytics.setConversionSendId("some-id");
        assertEquals("Conversion send Id is unable to be set", analytics.getConversionSendId(), "some-id");

        analytics.setConversionSendId(null);
        assertNull("Conversion send Id is unable to be cleared", analytics.getConversionSendId());
    }

    /**
     * Test adding an event
     */
    @Test
    public void testAddEvent() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.getEventId()).thenReturn("event-id");
        Mockito.when(event.getType()).thenReturn("event-type");
        Mockito.when(event.createEventPayload(Mockito.anyString())).thenReturn("event-data");
        Mockito.when(event.getTime()).thenReturn("1000");
        Mockito.when(event.isValid()).thenReturn(true);
        Mockito.when(event.getPriority()).thenReturn(Event.LOW_PRIORITY);


        analytics.addEvent(event);

        // Verify the intent contains the content values of the event
        Intent addEventIntent = shadowApplication.getNextStartedService();
        assertNotNull(addEventIntent);
        assertEquals("Should add an intent with action ADD", addEventIntent.getAction(), AnalyticsIntentHandler.ACTION_ADD);
        assertEquals(addEventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_ID), "event-id");
        assertEquals(addEventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_DATA), "event-data");
        assertEquals(addEventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_TYPE), "event-type");
        assertEquals(addEventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_TIME_STAMP), "1000");
        assertEquals(addEventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_SESSION_ID), analytics.getSessionId());
        assertEquals(addEventIntent.getIntExtra(AnalyticsIntentHandler.EXTRA_EVENT_PRIORITY, -100), Event.LOW_PRIORITY);

    }

    /**
     * Test adding an event when analytics is disabled through airship config.
     */
    @Test
    public void testAddEventDisabledAnalyticsConfig() {
        AirshipConfigOptions options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setAnalyticsEnabled(false)
                .build();

        analytics = new Analytics(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, UAirship.ANDROID_PLATFORM);

        analytics.addEvent(new AppForegroundEvent(100));
        Intent addEventIntent = shadowApplication.getNextStartedService();
        assertNull("Should not add events if analytics is disabled", addEventIntent);
    }

    /**
     * Test adding an event when analytics is disabled
     */
    @Test
    public void testAddEventDisabledAnalytics() {
        analytics.setEnabled(false);
        shadowApplication.clearStartedServices();

        analytics.addEvent(new AppForegroundEvent(100));

        Intent addEventIntent = shadowApplication.getNextStartedService();
        assertNull("Should not add events if analytics is disabled", addEventIntent);
    }

    /**
     * Test adding a null event
     */
    @Test
    public void testAddNullEvent() {
        analytics.addEvent(null);
        Intent addEventIntent = shadowApplication.getNextStartedService();
        assertNull("Should not start the event service to add a null event", addEventIntent);
    }

    /**
     * Test adding an invalid event
     */
    @Test
    public void testAddInvalidEvent() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.getEventId()).thenReturn("event-id");
        Mockito.when(event.getType()).thenReturn("event-type");
        Mockito.when(event.createEventPayload(Mockito.anyString())).thenReturn("event-data");
        Mockito.when(event.getEventId()).thenReturn("event-id");
        Mockito.when(event.getTime()).thenReturn("1000");
        Mockito.when(event.getPriority()).thenReturn(Event.HIGH_PRIORITY);

        Mockito.when(event.isValid()).thenReturn(false);

        analytics.addEvent(event);
        Intent addEventIntent = shadowApplication.getNextStartedService();
        assertNull("Should not start the event service to add a null event", addEventIntent);
    }

    /**
     * Test disabling analytics should start the event service to delete all events.
     */
    @Test
    public void testDisableAnalytics() {
        analytics.setEnabled(false);

        assertEquals(AnalyticsIntentHandler.ACTION_DELETE_ALL, shadowApplication.getNextStartedService().getAction());
    }

    /**
     * Test life cycle activity events when an activity is started
     */
    @SuppressLint("NewApi")
    public void testActivityLifeCycleEventsActivityStarted() {
        Activity activity = new Activity();

        TestApplication.getApplication().callback.onActivityStarted(activity);

        // The activity started is posted on the looper
        Shadows.shadowOf(Looper.myLooper()).runToEndOfTasks();

        // Verify that the activity monitor was called with auto instrumentation
        Mockito.verify(mockActivityMonitor).activityStarted(eq(activity), anyLong());
    }

    /**
     * Test life cycle activity events when an activity is stopped
     */
    @SuppressLint("NewApi")
    public void testActivityLifeCycleEventsActivityStopped() {
        Activity activity = new Activity();

        // The activity stopped is posted on the looper
        TestApplication.getApplication().callback.onActivityStopped(activity);

        // Verify that the activity monitor was called with auto instrumentation
        Mockito.verify(mockActivityMonitor).activityStopped(eq(activity), anyLong());
    }

    // TODO: Remove this test for 8.0.0 since AssociatedIdentifiers.Builder() has been deprecated
    /**
     * Test associating identifiers sends a associate identifiers event to the event service.
     */
    @Test
    public void testAssociateIdentifiers() {
        analytics.associateIdentifiers(new AssociatedIdentifiers.Builder().create());

        // Verify we started the event service to add the event
        Intent eventIntent = shadowApplication.getNextStartedService();
        assertEquals(AnalyticsIntentHandler.ACTION_ADD, eventIntent.getAction());
        assertEquals("associate_identifiers", eventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
    }

    /**
     * Test editAssociatedIdentifiers sends an associate identifiers event to the event service.
     */
    @Test
    public void testEditAssociatedIdentifiers() {
        analytics.editAssociatedIdentifiers()
                 .addIdentifier("customKey", "customValue")
                 .apply();

        // Verify we started the event service to add the event
        Intent eventIntent = shadowApplication.getNextStartedService();
        assertEquals(AnalyticsIntentHandler.ACTION_ADD, eventIntent.getAction());
        assertEquals("associate_identifiers", eventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));

        AssociatedIdentifiers storedIds = analytics.getAssociatedIdentifiers();
        assertEquals(storedIds.getIds().get("customKey"), "customValue");
        assertEquals(storedIds.getIds().size(), 1);
    }

    /**
     * Test that tracking event adds itself on background
     */
    @Test
    public void testTrackingEventBackground () {
        analytics.setEnabled(true);

        analytics.trackScreen("test_screen");

        // Make call to background
        activityMonitorListener.onBackground(0);

        // Verify we started the event service to add the event
        Intent eventIntent = shadowApplication.getNextStartedService();
        assertEquals(AnalyticsIntentHandler.ACTION_ADD, eventIntent.getAction());
        assertEquals("screen_tracking", eventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
    }

    /**
     * Test that tracking event adds itself upon adding a new screen
     */
    @Test
    public void testTrackingEventAddNewScreen () {

        analytics.trackScreen("test_screen_1");

        // Add another screen
        analytics.trackScreen("test_screen_2");

        // Verify we started the event service to add the event
        Intent eventIntent = shadowApplication.getNextStartedService();
        assertEquals(AnalyticsIntentHandler.ACTION_ADD, eventIntent.getAction());
        assertEquals("screen_tracking", eventIntent.getStringExtra(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
    }

    /**
     * Test that tracking event ignores duplicate tracking calls for same screen
     */
    @Test
    public void testTrackingEventAddSameScreen() {
        analytics.setEnabled(true);

        analytics.trackScreen("test_screen_1");

        // Add another screen
        analytics.trackScreen("test_screen_1");

        // Verify event service to add event is not started
        assertNull(shadowApplication.getNextStartedService());
    }


    /**
     * Test that foregrounding the app with advertising ID tracking enabled dispatches an intent to the
     * event service.
     */
    @Test
    public void testAdIdTrackingOnForeground() {
        // Test the preference settings.
        analytics.setAutoTrackAdvertisingIdEnabled(false);
        assertFalse(analytics.isAutoTrackAdvertisingIdEnabled());

        analytics.setAutoTrackAdvertisingIdEnabled(true);
        assertTrue(analytics.isAutoTrackAdvertisingIdEnabled());

        // Start analytics in the background.
        activityMonitorListener.onBackground(0);
        assertFalse(analytics.isAppInForeground());

        activityMonitorListener.onForeground(0);
        assertTrue(analytics.isAppInForeground());

        // An advertising ID update event is sent.
        Intent eventIntent = shadowApplication.getNextStartedService();
        assertEquals(AnalyticsIntentHandler.ACTION_UPDATE_ADVERTISING_ID, eventIntent.getAction());
    }
}
