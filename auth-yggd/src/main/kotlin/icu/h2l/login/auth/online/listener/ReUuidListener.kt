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

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import icu.h2l.api.event.auth.LoginReUuidEvent
import icu.h2l.login.auth.online.credential.YggdrasilHyperZoneCredential

/**
 * 响应 ReUuid 事件，销毁旧 Yggdrasil 凭证并以清除 UUID 建议的新凭证重新提交。
 *
 * Yggdrasil 名称由 Mojang 认证，不受 rename 影响，因此无需处理 LoginRenameEvent。
 * 该监听器以 [PostOrder.EARLY] 运行，确保在核心 `LoginReUuidListener` 之前完成凭证替换。
 *
 * ── 设计约定：reUuid 对 Yggdrasil 数据库匹配键无影响 ──
 *
 * Yggdrasil 认证的核心身份标识是 Mojang 颁发的玩家 UUID（以及对应的 entry UUID）：
 * - `yggdrasil_auth` 表以 `(entry_id, mojang_uuid)` 作为主匹配键，映射到 `profile_id`；
 * - reUuid 仅清除凭证中的 suggestedProfileCreateUuid，使核心逻辑在 Profile 表中
 *   选择一个不冲突的 UUID 来创建 Profile；这与 Yggdrasil 入口表自身的匹配路径无关：
 *   Yggdrasil 入口表的 mojang_uuid 来源于 Mojang 服务器的认证响应，始终保持不变；
 * - 后续登录通过 Mojang UUID 在 `yggdrasil_auth` 表中查找 `profile_id`，
 *   Profile 表的 `uuid` 列（内部生成，可能因 reUuid 而改变）不影响 Yggdrasil 的查找。
 *
 * 与离线模块的对比：
 * - 离线模块以规范化用户名（normalizedName）为匹配键，rename 会同步修改
 *   `offline_auth` 表中用于查找的 `name` 字段，是唯一"rename 影响 DB 匹配键"的模块；
 * - 离线模块的 reUuid 同理：只影响 Profile 的内部 UUID，不影响 `offline_auth` 表
 *   以 `name` 为键的查找路径。
 */
class ReUuidListener {

    @Subscribe(order = PostOrder.EARLY)
    fun onReUuid(event: LoginReUuidEvent) {
        val player = event.hyperZonePlayer
        val oldCredential = player.getSubmittedCredentials()
            .filterIsInstance<YggdrasilHyperZoneCredential>()
            .firstOrNull() ?: return

        // withNewSuggestedUuid(null) 将 suggestedProfileCreateUuid 置为 null，
        // 使核心 ReUuid 逻辑自行生成不冲突的 Profile UUID。
        // Yggdrasil 入口的 mojang_uuid 及 entry_id 均不变，匹配键不受影响。
        player.destroyCredential(oldCredential.channelId)
        player.submitCredential(oldCredential.withNewSuggestedUuid(null))
    }
}
