/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TagGroupIntentHandlerTest extends BaseTestCase {

    private Map<String, Set<String>> addTagsMap;
    private Map<String, Set<String>> removeTagsMap;
    private Bundle addTagsBundle;
    private Bundle removeTagsBundle;

    private TagGroupsApiClient tagGroupsClient;
    private NamedUser namedUser;
    private PushManager pushManager;
    private PreferenceDataStore dataStore;
    private TagGroupIntentHandler intentHandler;

    @Before
    public void setUp() {
        tagGroupsClient = Mockito.mock(TagGroupsApiClient.class);
        namedUser = Mockito.mock(NamedUser.class);
        pushManager = Mockito.mock(PushManager.class);

        TestApplication.getApplication().setNamedUser(namedUser);
        TestApplication.getApplication().setPushManager(pushManager);

        dataStore = TestApplication.getApplication().preferenceDataStore;

        intentHandler = new TagGroupIntentHandler(TestApplication.getApplication(), UAirship.shared(), dataStore, tagGroupsClient);

        Set<String> addTags = new HashSet<>();
        addTags.add("tag1");
        addTags.add("tag2");
        addTagsMap = new HashMap<>();
        addTagsMap.put("tagGroup", addTags);

        Set<String> removeTags = new HashSet<>();
        removeTags.add("tag4");
        removeTags.add("tag5");
        removeTagsMap = new HashMap<>();
        removeTagsMap.put("tagGroup", removeTags);

        addTagsBundle = new Bundle();
        for (Map.Entry<String, Set<String>> entry : addTagsMap.entrySet()) {
            addTagsBundle.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        removeTagsBundle = new Bundle();
        for (Map.Entry<String, Set<String>> entry : removeTagsMap.entrySet()) {
            removeTagsBundle.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    /**
     * Test update channel tag groups succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsSucceed() {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags("channelID", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient, Mockito.times(1)).updateChannelTags("channelID", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update channel tag groups without channel fails and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsNoChannelId() throws JsonException {
        // Return a null channel ID
        when(pushManager.getChannelId()).thenReturn(null);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags not called when channel ID doesn't exist
        verifyZeroInteractions(tagGroupsClient);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update channel tag groups fails if the status is 500 and save pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsServerError() throws JsonException {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags("channelID", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient).updateChannelTags("channelID", addTagsMap, removeTagsMap);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test don't update channel tags if both pendingAddTags and pendingRemoveTags are empty.
     */
    @Test
    public void testNoUpdateWithEmptyTags() {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Perform an update without specify new tags
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intentHandler.handleIntent(intent);

        // Verify it didn't cause a client update
        verifyZeroInteractions(tagGroupsClient);
    }

    /**
     * Test update channel tag groups fails if the status is 400 and clears pending tags.
     */
    @Test
    public void testUpdateChannelTagGroupsBadRequest() {
        // Return a channel ID
        when(pushManager.getChannelId()).thenReturn("channelID");

        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateChannelTags("channelID", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateChannelTags called
        Mockito.verify(tagGroupsClient).updateChannelTags("channelID", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update named user tags succeeds if the status is 200 and clears pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsSucceed() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Set up a 200 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(200);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateNamedUserTags called
        Mockito.verify(tagGroupsClient).updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update named user tags without named user ID fails and save pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsNoNamedUser() throws JsonException {
        // Return a null named user ID
        when(namedUser.getId()).thenReturn(null);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateNamedUserTags not called when channel ID doesn't exist
        verifyZeroInteractions(tagGroupsClient);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test update named user tags fails if the status is 500 and save pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsServerError() throws JsonException {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Set up a 500 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(500);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateNamedUserTags called
        Mockito.verify(tagGroupsClient).updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tags saved
        assertEquals(JsonValue.wrap(addTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertEquals(JsonValue.wrap(removeTagsMap).toString(), dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test don't update named user tags if both pendingAddTags and pendingRemoveTags are empty.
     */
    @Test
    public void testNoUpdateNamedUserWithEmptyTags() {
        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");
        Bundle emptyTagsBundle = new Bundle();

        // Perform an update without specify new tags
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_NAMED_USER_TAGS);
        intentHandler.handleIntent(intent);

        // Verify it didn't cause a client update
        verifyZeroInteractions(tagGroupsClient);
    }

    /**
     * Test update named user tags fails if the status is 400 and clears pending tags.
     */
    @Test
    public void testUpdateNamedUserTagsBadRequest() {

        // Return a named user ID
        when(namedUser.getId()).thenReturn("namedUserId");

        // Set up a 400 response
        Response response = Mockito.mock(Response.class);
        when(tagGroupsClient.updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap)).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_UPDATE_NAMED_USER_TAGS);
        intent.putExtra(TagGroupIntentHandler.EXTRA_ADD_TAG_GROUPS, addTagsBundle);
        intent.putExtra(TagGroupIntentHandler.EXTRA_REMOVE_TAG_GROUPS, removeTagsBundle);
        intentHandler.handleIntent(intent);

        // Verify updateNamedUserTags called
        Mockito.verify(tagGroupsClient).updateNamedUserTags("namedUserId", addTagsMap, removeTagsMap);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }

    /**
     * Test clear pending named user tags.
     */
    @Test
    public void testClearPendingNamedUserTags() throws JsonException {
        // Set non-empty pending tags
        dataStore.put(TagGroupIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrap(addTagsMap).toString());
        dataStore.put(TagGroupIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(removeTagsMap).toString());

        // Perform the update
        Intent intent = new Intent(TagGroupIntentHandler.ACTION_CLEAR_PENDING_NAMED_USER_TAGS);
        intentHandler.handleIntent(intent);

        // Verify pending tag groups are empty
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, null));
        assertNull(dataStore.getString(TagGroupIntentHandler.PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, null));
    }
}
