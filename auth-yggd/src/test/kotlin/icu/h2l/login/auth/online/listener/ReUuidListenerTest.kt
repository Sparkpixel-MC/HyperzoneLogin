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

package icu.h2l.login.auth.online.listener

import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.db.HyperZoneDatabaseManager
import icu.h2l.api.db.table.ProfileTable
import icu.h2l.api.event.auth.LoginReUuidEvent
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.api.profile.HyperZoneCredential
import icu.h2l.login.auth.online.credential.YggdrasilHyperZoneCredential
import icu.h2l.login.auth.online.db.EntryDatabaseHelper
import icu.h2l.login.auth.online.db.EntryTableManager
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * 测试 [ReUuidListener]：reuuid 场景及混合冲突场景（UUID 冲突通过 reuuid 解决后，
 * 凭证应清除 suggestedProfileCreateUuid，交由核心 ReUuid 逻辑选取新 UUID）。
 */
class ReUuidListenerTest {

    private val listener = ReUuidListener()
    private val authenticatedUuid: UUID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val suggestedUuid: UUID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

    private lateinit var entryDatabaseHelper: EntryDatabaseHelper
    private lateinit var player: FakeHyperZonePlayer
    private lateinit var initialCredential: YggdrasilHyperZoneCredential

    @BeforeEach
    fun setUp() {
        // Build a no-op EntryDatabaseHelper backed by a stub database manager
        val noopDatabaseManager = object : HyperZoneDatabaseManager {
            override val tablePrefix: String = ""
            override fun <T> executeTransaction(statement: () -> T): T = statement()
        }
        val entryTableManager = EntryTableManager(
            databaseManager = noopDatabaseManager,
            tablePrefix = "",
            profileTable = ProfileTable()
        )
        entryDatabaseHelper = EntryDatabaseHelper(noopDatabaseManager, entryTableManager)

        initialCredential = YggdrasilHyperZoneCredential(
            entryDatabaseHelper = entryDatabaseHelper,
            entryId = "mojang",
            authenticatedName = "MojangPlayer",
            authenticatedUUID = authenticatedUuid,
            suggestedProfileCreateUuid = suggestedUuid
        )
        player = FakeHyperZonePlayer("MojangPlayer")
        player.submitCredential(initialCredential)
    }

    // ─── ReUuid ────────────────────────────────────────────────────────────────

    @Test
    fun `reuuid clears suggestedProfileCreateUuid`() {
        listener.onReUuid(LoginReUuidEvent(player))

        val cred = singleCredential<YggdrasilHyperZoneCredential>(player)
        assertNull(cred.getSuggestedProfileCreateUuid())
    }

    @Test
    fun `reuuid preserves authenticatedName`() {
        listener.onReUuid(LoginReUuidEvent(player))

        val cred = singleCredential<YggdrasilHyperZoneCredential>(player)
        assertEquals("MojangPlayer", cred.getRegistrationName())
    }

    @Test
    fun `reuuid preserves authenticatedUUID in credentialId`() {
        listener.onReUuid(LoginReUuidEvent(player))

        val cred = singleCredential<YggdrasilHyperZoneCredential>(player)
        assertTrue(cred.credentialId.contains(authenticatedUuid.toString()))
    }

    @Test
    fun `reuuid original credential is not mutated`() {
        listener.onReUuid(LoginReUuidEvent(player))

        assertEquals(suggestedUuid, initialCredential.getSuggestedProfileCreateUuid())
    }

    @Test
    fun `reuuid does nothing when player has no yggdrasil credential`() {
        player.destroyCredential("yggdrasil")

        listener.onReUuid(LoginReUuidEvent(player))

        assertTrue(player.getSubmittedCredentials().isEmpty())
    }

    // ─── Mixed: uuid conflict resolved by reuuid, subsequent reuuid re-clears ──

    @Test
    fun `mixed scenario reuuid twice still yields null suggestedUuid`() {
        listener.onReUuid(LoginReUuidEvent(player))
        listener.onReUuid(LoginReUuidEvent(player))

        val cred = singleCredential<YggdrasilHyperZoneCredential>(player)
        assertNull(cred.getSuggestedProfileCreateUuid())
        assertEquals("MojangPlayer", cred.getRegistrationName())
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
