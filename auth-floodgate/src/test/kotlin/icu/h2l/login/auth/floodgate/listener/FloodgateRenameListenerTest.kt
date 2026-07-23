/*
 * This file is part of HyperZoneLogin, licensed under the GNU Affero General Public License v3.0 or later.
 *
 * Copyright (C) ksqeib (庆灵) <ksqeib@qq.com>
 * Copyright (C) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package icu.h2l.login.auth.floodgate.listener

import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.event.auth.LoginRenameEvent
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.profile.HyperZoneCredential
import icu.h2l.login.auth.floodgate.credential.FloodgateHyperZoneCredential
import io.mockk.mockk
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * 测试 [FloodgateRenameListener]：rename 场景及混合冲突场景（UUID 冲突已由 Floodgate Mojang UUID 固定，
 * 名称冲突通过 rename 解决后，凭证应反映新名称）。
 */
class FloodgateRenameListenerTest {

    private val listener = FloodgateRenameListener()
    private val trustedUuid: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val xuid: Long = 123456789L
    private val suggestedUuid: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    private lateinit var player: FakeHyperZonePlayer
    private lateinit var initialCredential: FloodgateHyperZoneCredential

    @BeforeEach
    fun setUp() {
        initialCredential = FloodgateHyperZoneCredential(
            repository = mockk(relaxed = true),
            trustedName = "BedrockPlayer",
            trustedUuid = trustedUuid,
            xuid = xuid,
            suggestedProfileCreateUuid = suggestedUuid
        )
        player = FakeHyperZonePlayer("BedrockPlayer")
        player.submitCredential(initialCredential)
    }

    // ─── Rename ────────────────────────────────────────────────────────────────

    @Test
    fun `rename replaces credential with new registration name`() {
        listener.onRename(LoginRenameEvent(player, "NewBedrockName"))

        val cred = singleCredential<FloodgateHyperZoneCredential>(player)
        assertEquals("NewBedrockName", cred.getRegistrationName())
    }

    @Test
    fun `rename preserves xuid-based credentialId (uuid unchanged)`() {
        listener.onRename(LoginRenameEvent(player, "NewBedrockName"))

        val cred = singleCredential<FloodgateHyperZoneCredential>(player)
        assertEquals(trustedUuid.toString(), cred.credentialId)
    }

    @Test
    fun `rename preserves suggestedProfileCreateUuid`() {
        listener.onRename(LoginRenameEvent(player, "NewBedrockName"))

        val cred = singleCredential<FloodgateHyperZoneCredential>(player)
        assertEquals(suggestedUuid, cred.getSuggestedProfileCreateUuid())
    }

    @Test
    fun `rename original credential is not mutated`() {
        listener.onRename(LoginRenameEvent(player, "AnotherName"))

        assertEquals("BedrockPlayer", initialCredential.getRegistrationName())
    }

    @Test
    fun `rename does nothing when player has no floodgate credential`() {
        player.destroyCredential("floodgate")

        listener.onRename(LoginRenameEvent(player, "Irrelevant"))

        assertTrue(player.getSubmittedCredentials().isEmpty())
    }

    // ─── Mixed: name conflict resolved by rename ───────────────────────────────

    @Test
    fun `mixed scenario rename twice yields final name`() {
        listener.onRename(LoginRenameEvent(player, "Bedrock1"))
        // After first rename, credential name is "Bedrock1" - rename again
        listener.onRename(LoginRenameEvent(player, "FinalName"))

        val cred = singleCredential<FloodgateHyperZoneCredential>(player)
        assertEquals("FinalName", cred.getRegistrationName())
    }

    @Test
    fun `single credential invariant - double submit without destroy throws`() {
        assertThrows(IllegalStateException::class.java) {
            player.submitCredential(initialCredential)
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private inline fun <reified T : HyperZoneCredential> singleCredential(p: FakeHyperZonePlayer): T {
        val creds = p.getSubmittedCredentials()
        assertEquals(1, creds.size, "Expected exactly one credential")
        return creds.single() as T
    }

    private class FakeHyperZonePlayer(override val clientOriginalName: String) : HyperZonePlayer {
        override val clientOriginalUUID: UUID = UUID.randomUUID()
        override val isOnlinePlayer: Boolean = false
        private val credentials = mutableListOf<HyperZoneCredential>()

        override fun hasAttachedProfile(): Boolean = false
        override fun canBind(): Boolean = false
        override fun overVerify() {}
        override fun resetVerify() { credentials.clear() }
        override fun sendMessage(message: Component) {}
        override fun getTemporaryGameProfile(): GameProfile = error("not used")
        override fun getAttachedGameProfile(): GameProfile = error("not used")

        override fun submitCredential(credential: HyperZoneCredential) {
            check(credentials.isEmpty()) {
                "已存在凭证 ${credentials.first().channelId}，必须先调用 destroyCredential()"
            }
            credentials += credential
        }

        override fun destroyCredential(channelId: String) {
            credentials.removeIf { it.channelId == channelId }
        }

        override fun getSubmittedCredentials(): List<HyperZoneCredential> = credentials.toList()
    }
}

