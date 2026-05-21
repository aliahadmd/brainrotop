package com.aliahad.brainrotop.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShortVideoDetectorTest {
    @Test
    fun normalWeChatVideoAccountEntryDoesNotBlock() {
        val result = ShortVideoDetector.detect(
            packageName = "com.tencent.mm",
            root = screen("微信", "聊天", "通讯录", "发现", "视频号", "朋友圈"),
            activityClassName = "com.tencent.mm.ui.LauncherUI",
        )

        assertNull(result)
    }

    @Test
    fun weChatChannelsBlocksOnTabAndActionPattern() {
        val result = ShortVideoDetector.detect(
            packageName = "com.tencent.mm",
            root = screen("Follow", "Friends", "Hot", "68.9k likes", "6.8k share", "弹幕"),
        )

        assertNotNull(result)
        assertEquals("wechat_channels", result?.ruleId)
    }

    @Test
    fun normalWeChatChatDoesNotBlock() {
        val result = ShortVideoDetector.detect(
            packageName = "com.tencent.mm",
            root = screen("Alice", "Type a message", "Voice call", "Send", "红包"),
            activityClassName = "com.tencent.mm.ui.LauncherUI",
        )

        assertNull(result)
    }

    @Test
    fun weChatFinderActivityBlocksEvenWhenVisibleTextIsHidden() {
        val result = ShortVideoDetector.detect(
            packageName = "com.tencent.mm",
            root = screen("68.9k", "45.5k", "602"),
            activityClassName = "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI",
        )

        assertNotNull(result)
        assertEquals("wechat_channels", result?.ruleId)
    }

    @Test
    fun youtubeShortsBlocks() {
        val result = ShortVideoDetector.detect(
            packageName = "com.google.android.youtube",
            root = screen("Shorts", "Like", "Comment", "Share"),
        )

        assertNotNull(result)
        assertEquals("youtube_shorts", result?.ruleId)
    }

    @Test
    fun facebookReelsBlocks() {
        val result = ShortVideoDetector.detect(
            packageName = "com.facebook.katana",
            root = screen("Reels", "Like", "Comment", "Share"),
        )

        assertNotNull(result)
        assertEquals("facebook_reels", result?.ruleId)
    }

    @Test
    fun instagramReelsBlocks() {
        val result = ShortVideoDetector.detect(
            packageName = "com.instagram.android",
            root = screen("Reels", "Like", "Comment", "Share"),
        )

        assertNotNull(result)
        assertEquals("instagram_reels", result?.ruleId)
    }

    @Test
    fun nonTargetAppDoesNotBlock() {
        val result = ShortVideoDetector.detect(
            packageName = "com.example.notes",
            root = screen("Short reminder", "Comment about work"),
        )

        assertNull(result)
    }

    private fun screen(vararg texts: String): NodeSnapshot =
        NodeSnapshot(
            packageName = "root",
            children = texts.map { text -> NodeSnapshot(text = text) },
        )
}
