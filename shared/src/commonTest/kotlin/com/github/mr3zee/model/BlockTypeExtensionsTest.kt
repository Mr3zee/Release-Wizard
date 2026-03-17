package com.github.mr3zee.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlockTypeExtensionsTest {

    @Test
    fun `requiredConnectionType maps all block types to connection types`() {
        assertEquals(ConnectionType.TEAMCITY, BlockType.TEAMCITY_BUILD.requiredConnectionType())
        assertEquals(ConnectionType.GITHUB, BlockType.GITHUB_ACTION.requiredConnectionType())
        assertEquals(ConnectionType.GITHUB, BlockType.GITHUB_PUBLICATION.requiredConnectionType())
        assertEquals(ConnectionType.SLACK, BlockType.SLACK_MESSAGE.requiredConnectionType())
    }

    @Test
    fun `configIdParameterKey returns correct keys for discoverable block types`() {
        assertEquals("buildTypeId", BlockType.TEAMCITY_BUILD.configIdParameterKey())
        assertEquals("workflowFile", BlockType.GITHUB_ACTION.configIdParameterKey())
    }

    @Test
    fun `configIdParameterKey returns null for block types without config discovery`() {
        assertNull(BlockType.GITHUB_PUBLICATION.configIdParameterKey())
        assertNull(BlockType.SLACK_MESSAGE.configIdParameterKey())
    }
}
