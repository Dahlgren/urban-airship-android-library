/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.location;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.SparseArray;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;

import java.util.ArrayList;
import java.util.List;

/**
 * High level interface for interacting with location.
 */
public class UALocationManager extends AirshipComponent {

    private static final String LAST_REQUESTED_LOCATION_OPTIONS_KEY = "com.urbanairship.location.LAST_REQUESTED_LOCATION_OPTIONS";
    private static final String LOCATION_UPDATES_ENABLED_KEY = "com.urbanairship.location.LOCATION_UPDATES_ENABLED";
    private static final String BACKGROUND_UPDATES_ALLOWED_KEY = "com.urbanairship.location.BACKGROUND_UPDATES_ALLOWED";
    private static final String LOCATION_OPTIONS_KEY = "com.urbanairship.location.LOCATION_OPTIONS";

    private final Messenger messenger;
    private final Context context;
    private Messenger serviceMessenger;

    private boolean isBound;
    private boolean isSubscribed;

    private int nextSingleLocationRequestId = 1;
    private final SparseArray<SingleLocationRequest> singleLocationRequests = new SparseArray<>();
    private final ActivityMonitor.Listener listener;
    private final PreferenceDataStore preferenceDataStore;
    private final ActivityMonitor activityMonitor;

    /**
     * List of location listeners.
     */
    private final List<LocationListener> locationListeners = new ArrayList<>();

    /**
     * Handles connections to the location service.
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.verbose("Location service connected.");
            UALocationManager.this.onServiceConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Logger.verbose("Location service disconnected.");
            UALocationManager.this.onServiceDisconnected();
        }
    };

    /**
     * When preferences are changed on the current process or other processes,
     * it will trigger the PreferenceChangeListener.  Instead of dealing
     * with the changes twice (one in the set method, one here), we will
     * just deal with changes when the listener notifies the manager.
     */
    private final PreferenceDataStore.PreferenceChangeListener preferenceChangeListener = new PreferenceDataStore.PreferenceChangeListener() {
        @Override
        public void onPreferenceChange(String key) {
            switch (key) {
                case BACKGROUND_UPDATES_ALLOWED_KEY:
                case LOCATION_UPDATES_ENABLED_KEY:
                case LOCATION_OPTIONS_KEY:
                    updateServiceConnection();
                    break;
            }
        }
    };


    /**
     * Creates a UALocationManager. Normally only one UALocationManager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getLocationManager()}.
     *
     * @param context Application context
     * @param preferenceDataStore The preferences data store.
     * @hide
     */
    public UALocationManager(@NonNull final Context context, @NonNull PreferenceDataStore preferenceDataStore, @NonNull ActivityMonitor activityMonitor) {
        this.context = context.getApplicationContext();
        this.preferenceDataStore = preferenceDataStore;
        this.messenger = new Messenger(new IncomingHandler(Looper.getMainLooper()));
        this.listener = new ActivityMonitor.Listener() {
            @Override
            public void onForeground(long time) {
                UALocationManager.this.updateServiceConnection();
            }

            @Override
            public void onBackground(long time) {
                UALocationManager.this.updateServiceConnection();
            }
        };
        this.activityMonitor = activityMonitor;
    }

    @Override
    protected void init() {
        preferenceDataStore.addListener(preferenceChangeListener);
        activityMonitor.addListener(listener);
        updateServiceConnection();
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeListener(listener);
    }


    /**
     * Checks if continuous location updates is enabled or not.
     * </p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @return <code>true</code> if location updates are enabled, otherwise
     * <code>false</code>.
     */
    public boolean isLocationUpdatesEnabled() {
        return preferenceDataStore.getBoolean(LOCATION_UPDATES_ENABLED_KEY, false);
    }

    /**
     * Enable or disable continuous location updates.
     * </p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @param enabled If location updates should be enabled or not.
     */
    public void setLocationUpdatesEnabled(boolean enabled) {
        preferenceDataStore.put(LOCATION_UPDATES_ENABLED_KEY, enabled);
    }

    /**
     * Checks if continuous location updates are allowed to continue
     * when the application is in the background.
     *
     * @return <code>true</code> if continuous location update are allowed,
     * otherwise <code>false</code>.
     */
    public boolean isBackgroundLocationAllowed() {
        return preferenceDataStore.getBoolean(BACKGROUND_UPDATES_ALLOWED_KEY, false);
    }

    /**
     * Enable or disable allowing continuous updates to continue in
     * the background.
     *
     * @param enabled If background updates are allowed in the background or not.
     */
    public void setBackgroundLocationAllowed(boolean enabled) {
        preferenceDataStore.put(BACKGROUND_UPDATES_ALLOWED_KEY, enabled);
    }

    /**
     * Sets the location request options for continuous updates.
     *
     * @param options The location request options, or null to reset the options to
     * the default settings.
     */
    public void setLocationRequestOptions(@Nullable LocationRequestOptions options) {
        preferenceDataStore.put(LOCATION_OPTIONS_KEY, options);
    }

    /**
     * Gets the location request options for continuous updates.  If no options
     * have been set, it will default to {@link LocationRequestOptions#createDefaultOptions()}.
     *
     * @return The continuous location request options.
     */
    @NonNull
    public LocationRequestOptions getLocationRequestOptions() {
        LocationRequestOptions options = null;

        String jsonString = preferenceDataStore.getString(LOCATION_OPTIONS_KEY, null);
        if (jsonString != null) {
            try {
                options = LocationRequestOptions.parseJson(jsonString);
            } catch (JsonException e) {
                Logger.error("UALocationManager - Failed parsing LocationRequestOptions from JSON: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                Logger.error("UALocationManager - Invalid LocationRequestOptions from JSON: " + e.getMessage());
            }
        }

        if (options == null) {
            options = new LocationRequestOptions.Builder().create();
        }

        return options;
    }

    /**
     * Sets the last update's location request options.
     *
     * @param lastUpdateOptions The last update's location request options.
     */
    void setLastUpdateOptions(@Nullable LocationRequestOptions lastUpdateOptions) {
        preferenceDataStore.put(LAST_REQUESTED_LOCATION_OPTIONS_KEY, lastUpdateOptions);
    }

    /**
     * Gets the last update's location request options.  If no options have been set, it will default to null.
     *
     * @return The last update's location request options.
     * */
    @Nullable
    LocationRequestOptions getLastUpdateOptions() {
        String jsonString = preferenceDataStore.getString(LAST_REQUESTED_LOCATION_OPTIONS_KEY, null);
        LocationRequestOptions lastUpdateOptions = null;

        if (jsonString != null) {
            try {
                lastUpdateOptions = LocationRequestOptions.parseJson(jsonString);
            } catch (JsonException e) {
                Logger.error("UALocationManager - Failed parsing LocationRequestOptions from JSON: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                Logger.error("UALocationManager - Invalid LocationRequestOptions from JSON: " + e.getMessage());
            }
        }

        return lastUpdateOptions;
    }

    /**
     * Adds a listener for locations updates.  The listener will only be notified
     * of continuous location updates, not single location requests.
     *
     * @param listener A location listener.
     */
    public void addLocationListener(@NonNull LocationListener listener) {
        synchronized (locationListeners) {
            locationListeners.add(listener);
            updateServiceConnection();
        }
    }

    /**
     * Removes location update listener.
     *
     * @param listener A location listener.
     */
    public void removeLocationListener(@NonNull LocationListener listener) {
        synchronized (locationListeners) {
            locationListeners.remove(listener);
            updateServiceConnection();
        }
    }

    /**
     * Records a single location using either the foreground request options
     * or the background request options depending on the application's state.
     * <p/>
     * The request may fail due to insufficient permissions.
     *
     * @return A cancelable object that can be used to cancel the request.
     */
    @NonNull
    public Cancelable requestSingleLocation() {
        return requestSingleLocation(null, getLocationRequestOptions());
    }

    /**
     * Records a single location using either the foreground request options
     * or the background request options depending on the application's state.
     *
     * @param locationCallback Callback with the location. The result may return a null location if
     * the request is unable to be made due to insufficient permissions.
     * @return A cancelable object that can be used to cancel the request.
     */
    @NonNull
    public Cancelable requestSingleLocation(@Nullable LocationCallback locationCallback) {
        return requestSingleLocation(locationCallback, getLocationRequestOptions());
    }

    /**
     * Records a single location using custom location request options.
     *
     * @param locationCallback Callback with the location. The result may return a null location or empty
     * Cancelable request if the request is unable to be made due to insufficient permissions.
     * @param requestOptions The location request options.
     * @return A cancelable object that can be used to cancel the request.
     * @throws IllegalArgumentException if the requestOptions is null.
     */
    @NonNull
    public Cancelable requestSingleLocation(@Nullable LocationCallback locationCallback, @NonNull LocationRequestOptions requestOptions) {
        //noinspection ConstantConditions
        if (requestOptions == null) {
            throw new IllegalArgumentException("Location request options cannot be null or invalid");
        }

        if (!isLocationPermitted()) {
            return new Cancelable() {
                @Override
                public void cancel() {}

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public boolean isCanceled() {
                    return true;
                }
            };
        }

        SingleLocationRequest request;
        synchronized (singleLocationRequests) {
            int id = nextSingleLocationRequestId++;
            request = new SingleLocationRequest(locationCallback, id, requestOptions);
            singleLocationRequests.put(id, request);
        }

        synchronized (this) {
            if (!isBound) {
                bindService();
            } else {
                request.sendLocationRequest();
            }
        }

        return request;
    }

    /**
     * Updates the service connection. Handles binding and subscribing to
     * the location service.
     */
    private void updateServiceConnection() {
        if (!isLocationPermitted()) {
            return;
        }

        if (isContinuousLocationUpdatesAllowed()) {
            synchronized (locationListeners) {
                if (!locationListeners.isEmpty()) {
                    if (isBound) {
                        subscribeUpdates();
                    } else {
                        // Once bound we will call updateServiceConnection again.
                        bindService();
                        return;
                    }
                }
            }
        } else {
            unsubscribeUpdates();
            synchronized (singleLocationRequests) {
                // unbind service
                if (singleLocationRequests.size() == 0) {
                    unbindService();
                }
            }
        }

        Intent intent = new Intent(context, LocationService.class)
                .setAction(LocationService.ACTION_CHECK_LOCATION_UPDATES);

        if (context.startService(intent) == null) {
            Logger.error("Unable to start location service. Check that the location service is added to the manifest.");
        }
    }

    /**
     * Starts and binds the location service.
     */
    private synchronized void bindService() {
        if (!isBound) {
            Logger.verbose("UALocationManager - Binding to location service.");

            Intent intent = new Intent(context, LocationService.class);
            if (context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                isBound = true;
            } else {
                Logger.error("Unable to bind to location service. Check that the location service is added to the manifest.");
            }
        }
    }

    /**
     * Subscribes to the location service for updates.
     */
    private synchronized void subscribeUpdates() {
        if (!isSubscribed && sendMessage(LocationService.MSG_SUBSCRIBE_UPDATES, 0, null)) {
            Logger.info("Subscribing to continuous location updates.");
            isSubscribed = true;
        }
    }

    /**
     * Unsubscribes to the location service for updates.
     */
    private synchronized void unsubscribeUpdates() {
        if (isSubscribed) {
            Logger.info("Unsubscribing from continuous location updates.");
            sendMessage(LocationService.MSG_UNSUBSCRIBE_UPDATES, 0, null);
            isSubscribed = false;
        }
    }

    /**
     * Unbinds the location service.
     * <p/>
     * Does not request the location service to stop, the location service will
     * stop itself on its own.
     */
    private synchronized void unbindService() {
        if (isBound) {
            Logger.verbose("UALocationManager - Unbinding to location service.");

            context.unbindService(serviceConnection);
            isBound = false;
        }
    }

    private synchronized void onServiceConnected(IBinder service) {
        serviceMessenger = new Messenger(service);

        // Send any location requests that we have in flight.
        synchronized (singleLocationRequests) {
            for (int i = 0; i < singleLocationRequests.size(); i++) {
                singleLocationRequests.valueAt(i).sendLocationRequest();
            }
        }
        updateServiceConnection();
    }

    private synchronized void onServiceDisconnected() {
        serviceMessenger = null;
        isSubscribed = false;
    }

    /**
     * Helper method that constructs and sends a message to the location
     * service.  The message will be populated with the supplied what and data
     * parameters and automatically set the replyto field to the UALocationManager's
     * messenger.
     *
     * @param what The message's what field.
     * @param arg1 The message's arg1 field.
     * @param data The message's data field.
     */
    private boolean sendMessage(int what, int arg1, @Nullable Bundle data) {
        if (serviceMessenger == null) {
            return false;
        }

        Message message = Message.obtain(null, what, arg1, 0);
        if (data != null) {
            message.setData(data);
        }

        message.replyTo = messenger;

        try {
            serviceMessenger.send(message);
            return true;
        } catch (RemoteException e) {
            Logger.debug("UALocationManager - Remote exception when sending message to location service");
        }
        return false;
    }

    /**
     * Handler of incoming messages from service.
     */
    private static class IncomingHandler extends Handler {

        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            UALocationManager manager = UAirship.shared().getLocationManager();

            switch (msg.what) {
                case LocationService.MSG_NEW_LOCATION_UPDATE:
                    Location location = (Location) msg.obj;
                    if (location != null) {

                        // Notify the listeners of the new location
                        synchronized (manager.locationListeners) {
                            for (LocationListener listener : manager.locationListeners) {
                                listener.onLocationChanged(location);
                            }
                        }
                    }
                    break;
                case LocationService.MSG_SINGLE_REQUEST_RESULT:
                    location = (Location) msg.obj;
                    int requestId = msg.arg1;

                    // Send any location requests that we have in flight.
                    synchronized (manager.singleLocationRequests) {
                        PendingResult<Location> request = manager.singleLocationRequests.get(requestId);
                        if (request != null) {
                            request.setResult(location);
                            manager.singleLocationRequests.remove(requestId);
                            manager.updateServiceConnection();
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * A request for a single location.
     */
    private class SingleLocationRequest extends PendingResult<Location> {

        private final LocationRequestOptions options;
        private final int requestId;

        /**
         * SingleLocationRequest constructor.
         *
         * @param callback The location callback.
         * @param requestId The request id.
         * @param options The location request options.
         */
        SingleLocationRequest(LocationCallback callback, int requestId, LocationRequestOptions options) {
            super(callback);
            this.requestId = requestId;
            this.options = options;
        }

        @Override
        protected void onCancel() {
            if (!isDone()) {
                sendMessage(LocationService.MSG_CANCEL_SINGLE_LOCATION_REQUEST, requestId, null);
            }

            synchronized (singleLocationRequests) {
                singleLocationRequests.remove(requestId);
            }
        }

        /**
         * Sends the single location request
         */
        synchronized void sendLocationRequest() {
            if (isDone()) {
                return;
            }

            Bundle data = new Bundle();
            data.putParcelable(LocationService.EXTRA_LOCATION_REQUEST_OPTIONS, options);
            sendMessage(LocationService.MSG_REQUEST_SINGLE_LOCATION, requestId, data);
        }
    }

    /**
     * Checks if location updates should be enabled.
     *
     * @return <code>true</code> if location updates should be enabled,
     * otherwise <code>false</code>.
     */
    boolean isContinuousLocationUpdatesAllowed() {
        return isLocationUpdatesEnabled() && (isBackgroundLocationAllowed() || UAirship.shared().getAnalytics().isAppInForeground());
    }

    /**
     * Checks for location permissions in the manifest.
     *
     * @return <code>true</code> if location is allowed,
     * otherwise <code>false</code>.
     */
    boolean isLocationPermitted() {
        try {
            int fineLocationPermissionCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION);
            int coarseLocationPermissionCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION);
            return fineLocationPermissionCheck == PackageManager.PERMISSION_GRANTED || coarseLocationPermissionCheck == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            Logger.error("UALocationManager - Unable to retrieve location permissions: " + e.getMessage());
            return false;
        }
    }
}
