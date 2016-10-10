/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.util.UAStringUtil;

import java.util.List;

/**
 * Location adapter for the standard Android location.
 * <p/>
 * The adapter tries to mimic the Fused Location Provider as much as possible by
 * automatically selecting the best provider based on the request settings. It
 * will reevaluate the best provider when providers are enabled and disabled.
 */
class StandardLocationAdapter implements LocationAdapter {

    private static final int STANDARD_LOCATION_ADAPTER_INTENT_FLAG = 2;

    private final LocationManager locationManager;
    private final Context context;
    private final Intent locationUpdatesIntent;

    private static String currentProvider;

    /**
     * Creates a standard location provider.
     *
     * @param context The application context.
     */
    StandardLocationAdapter(Context context) {
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.context = context;
        this.locationUpdatesIntent = new Intent(context, LocationService.class).setAction(UALocationProvider.ACTION_LOCATION_UPDATE);
    }

    @Override
    public void requestLocationUpdates(@NonNull LocationRequestOptions options) {
        Criteria criteria = createCriteria(options);
        PendingIntent intent = PendingIntent.getService(context, STANDARD_LOCATION_ADAPTER_INTENT_FLAG, locationUpdatesIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        locationManager.removeUpdates(intent);

        List<String> providers = locationManager.getProviders(criteria, false);
        if (providers != null) {
            for (String provider : providers) {
                Logger.verbose("StandardLocationAdapter - Update " +
                        "listening provider enable/disabled for: " + provider);
                //noinspection MissingPermission
                locationManager.requestLocationUpdates(provider, Long.MAX_VALUE, Float.MAX_VALUE, intent);
            }
        }

        String bestProvider = getBestProvider(criteria, options);
        if (!UAStringUtil.isEmpty(bestProvider)) {
            Logger.verbose("StandardLocationAdapter - Requesting location updates from provider: " + bestProvider);

            currentProvider = bestProvider;

            //noinspection MissingPermission
            locationManager.requestLocationUpdates(
                    bestProvider,
                    options.getMinTime(),
                    options.getMinDistance(),
                    intent);
        }
    }

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public void onSystemLocationProvidersChanged(@NonNull LocationRequestOptions options) {
        Criteria criteria = createCriteria(options);
        String bestProvider = getBestProvider(criteria, options);

        if (!UAStringUtil.isEmpty(currentProvider) && currentProvider.equals(bestProvider)) {
            Logger.verbose("StandardLocationAdapter - Already listening for updates from the best provider: " + currentProvider);
            return;
        }

        Logger.verbose("StandardLocationAdapter - Refreshing updates, best provider might of changed.");
        requestLocationUpdates(options);
    }

    @Override
    public void disconnect() {
    }

    @Override
    public void cancelLocationUpdates() {
        PendingIntent pendingIntent = PendingIntent.getService(context, STANDARD_LOCATION_ADAPTER_INTENT_FLAG, locationUpdatesIntent, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            locationManager.removeUpdates(pendingIntent);
            pendingIntent.cancel();
        }

        Logger.verbose("StandardLocationAdapter - Canceling location updates.");
        currentProvider = null;
    }

    @Override
    @NonNull
    public PendingResult<Location> requestSingleLocation(@NonNull LocationCallback locationCallback, @NonNull LocationRequestOptions options) {
        return new SingleLocationRequest(locationCallback, options);
    }

    @Override
    public boolean isUpdatesRequested() {
        return PendingIntent.getService(context, STANDARD_LOCATION_ADAPTER_INTENT_FLAG, locationUpdatesIntent, PendingIntent.FLAG_NO_CREATE) != null;
    }


    /**
     * Creates a location criteria from a LocationRequestOptions.
     *
     * @param options The locationRequestOptions.
     * @return A criteria created from the supplied options.
     */
    private Criteria createCriteria(@NonNull LocationRequestOptions options) {

        Criteria criteria = new Criteria();

        switch (options.getPriority()) {
            case LocationRequestOptions.PRIORITY_HIGH_ACCURACY:
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setPowerRequirement(Criteria.POWER_HIGH);
                break;
            case LocationRequestOptions.PRIORITY_BALANCED_POWER_ACCURACY:
                criteria.setAccuracy(Criteria.ACCURACY_COARSE);
                criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
                break;
            case LocationRequestOptions.PRIORITY_LOW_POWER:
            case LocationRequestOptions.PRIORITY_NO_POWER:
                criteria.setAccuracy(Criteria.NO_REQUIREMENT);
                criteria.setPowerRequirement(Criteria.POWER_LOW);
                break;
        }

        return criteria;
    }

    /**
     * Gets the best provider for the given criteria and location request options.
     *
     * @param criteria The criteria that the returned providers must match.
     * @param options The location request options.
     * @return The best provider, or null if one does not exist.
     */
    private String getBestProvider(@NonNull Criteria criteria, @NonNull LocationRequestOptions options) {
        if (options.getPriority() == LocationRequestOptions.PRIORITY_NO_POWER) {
            List<String> availableProviders = locationManager.getProviders(criteria, true);
            if (availableProviders == null) {
                return null;
            }

            if (availableProviders.contains(LocationManager.PASSIVE_PROVIDER)) {
                return LocationManager.PASSIVE_PROVIDER;
            } else {
                return null;
            }
        } else {
            return locationManager.getBestProvider(criteria, true);
        }
    }

    /**
     * Class that encapsulated the actual request to the standard Android
     * location.
     */
    private class SingleLocationRequest extends PendingResult<Location> {

        private final Criteria criteria;
        private final LocationRequestOptions options;
        private String currentProvider = null;

        private final AndroidLocationListener currentProviderListener;
        private final AndroidLocationListener providerEnabledListeners;

        /**
         * SingleLocationRequest constructor.
         *
         * @param locationCallback The location callback.
         * @param options The locationRequestOptions.
         */
        SingleLocationRequest(@NonNull LocationCallback locationCallback, @NonNull final LocationRequestOptions options) {
            super(locationCallback);
            this.options = options;
            this.criteria = createCriteria(options);

            currentProviderListener = new AndroidLocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    stopUpdates();
                    setResult(location);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    Logger.verbose("StandardLocationAdapter - Provider disabled: " + provider);
                    synchronized (this) {
                        if (!isDone()) {
                            listenForLocationChanges();
                        }
                    }
                }
            };

            providerEnabledListeners = new AndroidLocationListener() {
                @Override
                public void onProviderEnabled(String provider) {
                    Logger.verbose("StandardLocationAdapter - Provider enabled: " + provider);
                    synchronized (this) {
                        if (!isDone()) {
                            String bestProvider = getBestProvider(criteria, options);
                            if (bestProvider != null && !bestProvider.equals(currentProvider)) {
                                listenForLocationChanges();
                            }
                        }
                    }
                }
            };

            if (options.getPriority() != LocationRequestOptions.PRIORITY_NO_POWER) {
                listenForProvidersEnabled();
            }

            listenForLocationChanges();
        }

        private void listenForLocationChanges() {
            if (currentProvider != null) {
                //noinspection MissingPermission
                locationManager.removeUpdates(currentProviderListener);
            }

            String bestProvider = getBestProvider(criteria, options);

            currentProvider = bestProvider;

            if (bestProvider != null) {
                Logger.verbose("StandardLocationAdapter - Single request using provider: " + bestProvider);
                //noinspection MissingPermission
                locationManager.requestLocationUpdates(bestProvider, 0, 0, currentProviderListener);
            }
        }

        /**
         * Adds a listener to every provider to be notified when providers
         * are enabled/disabled.
         */
        private void listenForProvidersEnabled() {
            List<String> providers = locationManager.getProviders(criteria, false);
            if (providers != null) {
                for (String provider : providers) {
                    Logger.verbose("StandardLocationAdapter - Single location request " +
                            "listening provider enable/disabled for: " + provider);

                    //noinspection MissingPermission
                    locationManager.requestLocationUpdates(provider,
                            Long.MAX_VALUE,
                            Float.MAX_VALUE,
                            providerEnabledListeners);
                }
            }
        }

        @Override
        protected void onCancel() {
            Logger.verbose("StandardLocationAdapter - Canceling single request.");
            stopUpdates();
        }

        /**
         * Stop listening for updates.
         */
        private void stopUpdates() {
            //noinspection MissingPermission
            locationManager.removeUpdates(currentProviderListener);

            //noinspection MissingPermission
            locationManager.removeUpdates(providerEnabledListeners);
        }
    }

    /**
     * Android location listener used to listen for changes on the current best provider.
     */
    private static class AndroidLocationListener implements android.location.LocationListener {
        @Override
        public void onLocationChanged(Location location) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }
}
