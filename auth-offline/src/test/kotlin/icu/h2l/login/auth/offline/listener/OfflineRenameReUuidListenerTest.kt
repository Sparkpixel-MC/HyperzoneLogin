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

package icu.h2l.login.auth.offline.listener

import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.event.auth.LoginRenameEvent
import icu.h2l.api.event.auth.LoginReUuidEvent
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.profile.HyperZoneCredential
import icu.h2l.login.auth.offline.service.OfflineHyperZoneCredential
import icu.h2l.login.auth.offline.service.PendingOfflineRegistrationManager
import icu.h2l.login.auth.offline.util.ExtraUuidUtils
import io.mockk.mockk
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * 测试 [OfflineRenameReUuidListener] 事件监听行为：
 * rename、reuuid 及混合场景（名称和 UUID 同时冲突后依次解决）。
 */
class OfflineRenameReUuidListenerTest {

    private val listener = OfflineRenameReUuidListener()
    private lateinit var pendingRegistrations: PendingOfflineRegistrationManager
    private lateinit var pendingId: UUID
    private lateinit var player: FakeHyperZonePlayer
    private lateinit var initialCredential: OfflineHyperZoneCredential

    @BeforeEach
    fun setUp() {
        pendingRegistrations = PendingOfflineRegistrationManager()
        pendingId = UUID.randomUUID()
        pendingRegistrations.put(
            PendingOfflineRegistrationManager.PendingOfflineRegistration(
                credentialUuid = pendingId,
                normalizedName = "player1",
                passwordHash = "hash",
                hashFormat = "sha256"
            )
        )
        initialCredential = OfflineHyperZoneCredential(
            repository = mockk(relaxed = true),
            pendingRegistrations = pendingRegistrations,
            registrationName = "Player1",
            normalizedName = "player1",
            pendingRegistrationId = pendingId,
            passProfileCreateUuid = true
        )
        player = FakeHyperZonePlayer("Player1")
        player.submitCredential(initialCredential)
    }

    // ─── Rename ────────────────────────────────────────────────────────────────

    @Test
    fun `rename replaces credential with new name`() {
        listener.onRename(LoginRenameEvent(player, "Player2"))

        val cred = singleCredential<OfflineHyperZoneCredential>(player)
        assertEquals("Player2", cred.getRegistrationName())
    }

    @Test
    fun `rename updates pending registration normalizedName`() {
        listener.onRename(LoginRenameEvent(player, "NewName"))

        assertEquals("newname", pendingRegistrations.get(pendingId)?.normalizedName)
    }

    @Test
    fun `rename preserves suggestedProfileCreateUuid based on new name`() {
        listener.onRename(LoginRenameEvent(player, "NewName"))

        val cred = singleCredential<OfflineHyperZoneCredential>(player)
        assertEquals(ExtraUuidUtils.getNormalOfflineUUID("NewName"), cred.getSuggestedProfileCreateUuid())
    }

    @Test
    fun `rename does nothing when player has no offline credential`() {
        player.destroyCredential("offline")

        listener.onRename(LoginRenameEvent(player, "Irrelevant"))

        assertTrue(player.getSubmittedCredentials().isEmpty())
    }

    // ─── ReUuid ────────────────────────────────────────────────────────────────

    @Test
    fun `reuuid replaces credential with no suggested uuid`() {
        listener.onReUuid(LoginReUuidEvent(player))

        val cred = singleCredential<OfflineHyperZoneCredential>(player)
        assertNull(cred.getSuggestedProfileCreateUuid())
    }

    @Test
    fun `reuuid preserves registration name`() {
        listener.onReUuid(LoginReUuidEvent(player))

        val cred = singleCredential<OfflineHyperZoneCredential>(player)
        assertEquals("Player1", cred.getRegistrationName())
    }

    @Test
    fun `reuuid original credential keeps suggestedUuid (immutability)`() {
        assertNotNull(initialCredential.getSuggestedProfileCreateUuid())

        listener.onReUuid(LoginReUuidEvent(player))

        // original object is unchanged
        assertNotNull(initialCredential.getSuggestedProfileCreateUuid())
    }

    @Test
    fun `reuuid does nothing when player has no offline credential`() {
        player.destroyCredential("offline")

        listener.onReUuid(LoginReUuidEvent(player))

        assertTrue(player.getSubmittedCredentials().isEmpty())
    }

    // ─── Mixed: rename + reuuid ────────────────────────────────────────────────

    @Test
    fun `rename then reuuid yields new name with no suggested uuid`() {
        listener.onRename(LoginRenameEvent(player, "Player2"))
        listener.onReUuid(LoginReUuidEvent(player))

        val cred = singleCredential<OfflineHyperZoneCredential>(player)
        assertEquals("Player2", cred.getRegistrationName())
        assertNull(cred.getSuggestedProfileCreateUuid())
    }

    @Test
    fun `after rename then reuuid pendingRegistration reflects final renamed name`() {
        listener.onRename(LoginRenameEvent(player, "FinalName"))
        listener.onReUuid(LoginReUuidEvent(player))

        assertEquals("finalname", pendingRegistrations.get(pendingId)?.normalizedName)
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

    /**
     * 严格单凭证玩家实现，与 [icu.h2l.login.player.VelocityHyperZonePlayer] 保持相同约束。
     */
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
