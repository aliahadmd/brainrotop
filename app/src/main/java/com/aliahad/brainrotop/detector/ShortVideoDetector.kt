package com.aliahad.brainrotop.detector

data class DetectionResult(
    val packageName: String,
    val appLabel: String,
    val ruleId: String,
    val reason: String,
    val confidence: Int,
)

data class AppRule(
    val id: String,
    val appLabel: String,
    val packageNames: Set<String>,
    val mode: RuleMode,
)

enum class RuleMode {
    DedicatedShortVideoApp,
    WeChatChannels,
    MixedShortVideoSurface,
}

object ShortVideoDetector {
    val rules: List<AppRule> = listOf(
        AppRule(
            id = "wechat_channels",
            appLabel = "WeChat Channels",
            packageNames = setOf("com.tencent.mm"),
            mode = RuleMode.WeChatChannels,
        ),
        AppRule(
            id = "youtube_shorts",
            appLabel = "YouTube Shorts",
            packageNames = setOf("com.google.android.youtube"),
            mode = RuleMode.MixedShortVideoSurface,
        ),
        AppRule(
            id = "facebook_reels",
            appLabel = "Facebook Reels",
            packageNames = setOf("com.facebook.katana"),
            mode = RuleMode.MixedShortVideoSurface,
        ),
        AppRule(
            id = "instagram_reels",
            appLabel = "Instagram Reels",
            packageNames = setOf("com.instagram.android", "com.instagram.barcelona"),
            mode = RuleMode.MixedShortVideoSurface,
        ),
        AppRule(
            id = "xiaohongshu_video",
            appLabel = "Xiaohongshu Video",
            packageNames = setOf("com.xingin.xhs"),
            mode = RuleMode.MixedShortVideoSurface,
        ),
        AppRule(
            id = "reddit_video",
            appLabel = "Reddit Video",
            packageNames = setOf("com.reddit.frontpage"),
            mode = RuleMode.MixedShortVideoSurface,
        ),
        AppRule(
            id = "dedicated_short_video",
            appLabel = "Short-video app",
            packageNames = setOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
                "com.smile.gifmaker",
                "com.kuaishou.nebula",
            ),
            mode = RuleMode.DedicatedShortVideoApp,
        ),
    )

    fun detect(
        packageName: String?,
        root: NodeSnapshot?,
        activityClassName: String? = null,
    ): DetectionResult? {
        val normalizedPackage = packageName?.takeIf { it.isNotBlank() } ?: return null
        val rule = rules.firstOrNull { normalizedPackage in it.packageNames } ?: return null
        val screen = ScreenSignals.from(root, activityClassName)

        return when (rule.mode) {
            RuleMode.DedicatedShortVideoApp -> DetectionResult(
                packageName = normalizedPackage,
                appLabel = rule.appLabel,
                ruleId = rule.id,
                reason = "Dedicated short-video app opened",
                confidence = 100,
            )

            RuleMode.WeChatChannels -> detectWeChatChannels(normalizedPackage, rule, screen)
            RuleMode.MixedShortVideoSurface -> detectMixedSurface(normalizedPackage, rule, screen)
        }
    }

    private fun detectWeChatChannels(
        packageName: String,
        rule: AppRule,
        screen: ScreenSignals,
    ): DetectionResult? {
        val finderActivitySignal = screen.containsAny(
            "finderhome",
            "finderhomeaffinityui",
            "com.tencent.mm.plugin.finder",
        )
        if (finderActivitySignal) {
            return DetectionResult(
                packageName = packageName,
                appLabel = rule.appLabel,
                ruleId = rule.id,
                reason = "WeChat Channels activity detected",
                confidence = 96,
            )
        }

        val topTabs = screen.countDistinctMatches(
            "关注",
            "朋友",
            "热门",
            "推荐",
            "follow",
            "friends",
            "hot",
            "for you",
        )
        val actions = screen.countDistinctMatches(
            "点赞",
            "评论",
            "转发",
            "分享",
            "收藏",
            "like",
            "comment",
            "share",
            "favorite",
            "弹",
        )
        val videoCues = screen.countDistinctMatches(
            "作者",
            "原声",
            "秒",
            "合集",
            "不感兴趣",
            "danmaku",
            "video",
        )

        if (topTabs >= 2 && (actions >= 2 || videoCues >= 2)) {
            return DetectionResult(
                packageName = packageName,
                appLabel = rule.appLabel,
                ruleId = rule.id,
                reason = "WeChat Channels tab/action pattern detected",
                confidence = 88,
            )
        }

        return null
    }

    private fun detectMixedSurface(
        packageName: String,
        rule: AppRule,
        screen: ScreenSignals,
    ): DetectionResult? {
        val directSignals = when (packageName) {
            "com.google.android.youtube" -> listOf("shorts", "shorts_video", "shorts shelf")
            "com.facebook.katana" -> listOf("reels", "reel", "watch reels")
            "com.instagram.android", "com.instagram.barcelona" -> listOf("reels", "reel")
            "com.xingin.xhs" -> listOf("视频", "小红书视频", "弹幕", "沉浸")
            "com.reddit.frontpage" -> listOf("shorts", "reels", "video player", "watch more videos")
            else -> listOf("shorts", "reels", "short video")
        }

        if (screen.containsAny(*directSignals.toTypedArray())) {
            return DetectionResult(
                packageName = packageName,
                appLabel = rule.appLabel,
                ruleId = rule.id,
                reason = "${rule.appLabel} keyword detected",
                confidence = 94,
            )
        }

        val videoWords = screen.countDistinctMatches(
            "video",
            "视频",
            "播放",
            "player",
            "弹幕",
        )
        val actionWords = screen.countDistinctMatches(
            "like",
            "comment",
            "share",
            "点赞",
            "评论",
            "分享",
            "转发",
        )
        val feedWords = screen.countDistinctMatches(
            "follow",
            "for you",
            "hot",
            "推荐",
            "关注",
            "热门",
        )

        if (videoWords >= 1 && actionWords >= 2 && feedWords >= 1) {
            return DetectionResult(
                packageName = packageName,
                appLabel = rule.appLabel,
                ruleId = rule.id,
                reason = "Short-video feed pattern detected",
                confidence = 82,
            )
        }

        return null
    }

    private data class ScreenSignals(
        val fields: List<String>,
        val joined: String,
    ) {
        fun containsAny(vararg needles: String): Boolean =
            needles.any { needle -> joined.contains(needle.normalize()) }

        fun countDistinctMatches(vararg needles: String): Int =
            needles.count { needle -> joined.contains(needle.normalize()) }

        companion object {
            fun from(root: NodeSnapshot?, activityClassName: String?): ScreenSignals {
                val fields = sequenceOf(activityClassName)
                    .plus(
                        root
                            ?.flatten()
                            ?.flatMap { node ->
                                sequenceOf(
                                    node.packageName,
                                    node.className,
                                    node.text,
                                    node.contentDescription,
                                    node.viewIdResourceName,
                                )
                            }
                            ?: emptySequence(),
                    )
                    .mapNotNull { value -> value?.normalize()?.takeIf { it.isNotBlank() } }
                    .distinct()
                    .toList()

                return ScreenSignals(fields = fields, joined = fields.joinToString(separator = " "))
            }
        }
    }
}

private fun String.normalize(): String =
    lowercase().replace(Regex("\\s+"), " ").trim()
