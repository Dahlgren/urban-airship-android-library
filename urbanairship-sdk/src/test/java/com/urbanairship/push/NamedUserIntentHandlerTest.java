/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.HttpURLConnection;
import java.util.UUID;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class NamedUserIntentHandlerTest extends BaseTestCase {

    private NamedUserApiClient namedUserClient;
    private NamedUser namedUser;
    private PushManager pushManager;
    private PreferenceDataStore dataStore;
    private NamedUserIntentHandler intentHandler;

    private String changeToken;

    @Before
    public void setup() {
        namedUserClient = Mockito.mock(NamedUserApiClient.class);
        namedUser = Mockito.mock(NamedUser.class);
        pushManager = Mockito.mock(PushManager.class);

        TestApplication.getApplication().setNamedUser(namedUser);
        TestApplication.getApplication().setPushManager(pushManager);

        dataStore = TestApplication.getApplication().preferenceDataStore;

        changeToken = UUID.randomUUID().toString();
        when(namedUser.getChangeToken()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return changeToken;
            }
        });

        intentHandler = new NamedUserIntentHandler(TestApplication.getApplication(), UAirship.shared(),
                dataStore, namedUserClient);
    }

    /**
     * Test associate named user succeeds if the status is 2xx.
     */
    @Test
    public void testAssociateNamedUserSucceed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn("namedUserID");

        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Set a new change token to force an update
            changeToken = UUID.randomUUID().toString();

            // Set up a 2xx response
            Response response = Mockito.mock(Response.class);
            when(namedUserClient.associate("namedUserID", "channelID")).thenReturn(response);
            when(response.getStatus()).thenReturn(statusCode);

            // Perform the update
            Intent intent = new Intent(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER);
            intentHandler.handleIntent(intent);

            // Verify the update was performed
            verify(namedUserClient).associate("namedUserID", "channelID");

            // Verify the last change token was updated
            assertEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));

            // Reset the mocks so we can verify again
            reset(namedUserClient);
        }
    }

    /**
     * Test associate named user fails if the status is 403
     */
    @Test
    public void testAssociateNamedUserFailed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn("namedUserID");

        // Set up a 403 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
        when(namedUserClient.associate("namedUserID", "channelID")).thenReturn(response);

        // Perform the update
        Intent intent = new Intent(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER);
        intentHandler.handleIntent(intent);

        // Verify the update was performed
        verify(namedUserClient).associate("namedUserID", "channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test disassociate named user succeeds if the status is 2xx.
     */
    @Test
    public void testDisassociateNamedUserSucceed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn(null);

        for (int statusCode = 200; statusCode < 300; statusCode++) {
            // Set a new change token to force an update
            changeToken = UUID.randomUUID().toString();

            // Set up a 2xx response
            Response response = Mockito.mock(Response.class);
            when(namedUserClient.disassociate("channelID")).thenReturn(response);
            when(response.getStatus()).thenReturn(statusCode);

            // Perform the update
            Intent intent = new Intent(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER);
            intentHandler.handleIntent(intent);

            // Verify the update was performed
            verify(namedUserClient).disassociate("channelID");

            // Verify the last change token was updated
            assertEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));

            // Reset the mocks so we can verify again
            reset(namedUserClient);
        }
    }

    /**
     * Test disassociate named user fails if status is not 200.
     */
    @Test
    public void testDisassociateNamedUserFailed() {
        when(pushManager.getChannelId()).thenReturn("channelID");
        when(namedUser.getId()).thenReturn(null);

        // Set up a 404 response
        Response response = Mockito.mock(Response.class);
        when(response.getStatus()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        when(namedUserClient.disassociate("channelID")).thenReturn(response);

        // Perform the update
        Intent intent = new Intent(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER);
        intentHandler.handleIntent(intent);

        // Verify the update was performed
        verify(namedUserClient).disassociate("channelID");

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test associate without channel fails.
     */
    @Test
    public void testAssociateNamedUserFailedNoChannel() {
        when(pushManager.getChannelId()).thenReturn(null);
        when(namedUser.getId()).thenReturn("namedUserID");

        // Perform the update
        Intent intent = new Intent(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER);
        intentHandler.handleIntent(intent);

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(namedUserClient);

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

    /**
     * Test disassociate without channel fails.
     */
    @Test
    public void testDisassociateNamedUserFailedNoChannel() {
        when(pushManager.getChannelId()).thenReturn(null);
        when(namedUser.getId()).thenReturn(null);

        // Perform the update
        Intent intent = new Intent(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER);
        intentHandler.handleIntent(intent);

        // Verify associate not called when channel ID doesn't exist
        verifyZeroInteractions(namedUserClient);

        // Verify the last change token was not updated
        assertNotEquals(changeToken, dataStore.getString(NamedUserIntentHandler.LAST_UPDATED_TOKEN_KEY, null));
    }

}
