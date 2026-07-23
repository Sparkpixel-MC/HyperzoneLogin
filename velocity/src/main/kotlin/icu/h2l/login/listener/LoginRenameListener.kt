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

package icu.h2l.login.listener

import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.auth.LoginRenameEvent
import icu.h2l.api.profile.CredentialChannelRegistryProvider
import icu.h2l.login.HyperZoneLoginMain

class LoginRenameListener {
    @Subscribe
    fun onLoginRename(event: LoginRenameEvent) {
        val player = event.hyperZonePlayer
        if (player.hasAttachedProfile()) {
            return
        }

        val credentials = player.getSubmittedCredentials()
        val credential = credentials.singleOrNull { it.getBoundProfileId() == null } ?: return

        // 检查凭证渠道能力：若禁止注册则抛出异常，通知调用方（如 /rename 命令）
        val channelAbility = CredentialChannelRegistryProvider.getOrNull()?.getChannelAbility(credential.channelId)
        if (channelAbility?.canRegister == false) {
            throw IllegalStateException("当前渠道 [${credential.channelId}] 已被管理员禁止新玩家注册")
        }

        val profileService = HyperZoneLoginMain.getInstance().profileService
        if (!profileService.canCreate(credential)) {
            return
        }

        val createdProfile = profileService.create(credential)
        profileService.bindSubmittedCredentials(player, createdProfile.id)
        profileService.attachProfile(player, createdProfile.id)
            ?: throw IllegalStateException("rename 后 attach Profile 失败: ${createdProfile.id}")
    }
}
