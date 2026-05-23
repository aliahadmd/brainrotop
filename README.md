# Brainrotop

Brainrotop is a personal Android app that helps interrupt short-video loops and limit long screen sessions. It is designed for private use on your own phone.

## What It Does

- Blocks short-video surfaces such as WeChat Channels, YouTube Shorts, Facebook Reels, Instagram Reels, Xiaohongshu video feeds, Reddit video feeds, Douyin, TikTok, and Kuaishou.
- Keeps normal app use available when possible, especially normal WeChat chats.
- Can limit each continuous screen-on session. For example, if the limit is 5 minutes, Brainrotop starts counting when the screen turns on and blocks when the time is up.
- Shows a warning before the screen-time block.
- Tracks simple local analytics on your phone, such as blocks, warnings, sessions, and recent trends.
- Follows your phone's light or dark mode automatically.

## First-Time Setup

1. Install and open Brainrotop.
2. Go to the `Setup` tab.
3. Tap `Open Accessibility Settings`.
4. Find and enable `Brainrotop blocker`.
5. Return to Brainrotop.
6. Tap `Open Xiaomi App Protection` and allow Brainrotop to run in the background.
7. Tap `Allow Battery Exemption` if your phone asks for it.

On Xiaomi / HyperOS, background permissions matter a lot. If Brainrotop stops working after some time, check Autostart, Battery Saver, and App Lock / background restrictions in Xiaomi Security settings.

## Daily Use

Open Brainrotop when you want to change settings or check your progress.

The main tabs are:

- `Overview`: Shows protection status and screen-time limit controls.
- `Analytics`: Shows today's screen time, sessions, warnings, blocks, top blocked app, and a 7-day block trend.
- `Rules`: Shows which app surfaces Brainrotop protects.
- `Setup`: Opens phone settings, shows a test block screen, and lets you reset analytics.

## Screen-Time Limit

The screen-time limit counts from when the phone screen turns on.

Example:

- Limit is set to 5 minutes.
- You turn the screen on.
- Brainrotop warns you at about 4 minutes.
- Brainrotop blocks the screen at about 5 minutes.
- Turning the screen off resets the session.

To test quickly, set the limit to `1 min`. The warning should appear immediately or very soon after the screen turns on, and the block should happen at about 1 minute.

## Short-Video Blocking

When Brainrotop detects a protected short-video surface, it exits the app screen and shows a block screen.

For WeChat, Brainrotop tries to block Channels / 视频号 while leaving normal chat usable.

If a short-video page is not blocked immediately, leave the accessibility service enabled and try reopening that page. Some apps load their screen content slowly, so detection can happen after the UI appears.

## Analytics

Analytics are stored only on your phone.

Brainrotop does not upload your analytics anywhere.

The analytics section can show:

- Today's screen time
- Number of screen sessions
- Screen-time warnings
- Screen-time blocks
- Short-video blocks
- Top blocked app
- 7-day block trend

You can clear all analytics from the `Setup` tab with `Reset Analytics`.

## Troubleshooting

If blocking does not work:

- Make sure `Brainrotop blocker` is enabled in Accessibility Settings.
- Make sure Xiaomi / HyperOS is not stopping Brainrotop in the background.
- Allow battery exemption for Brainrotop.
- Open Brainrotop once after installing an update.
- Turn the accessibility service off and on again if Android seems stuck.

If analytics do not update:

- Leave Brainrotop open for a few seconds; the dashboard refreshes automatically.
- Remember that today's screen-time total updates after a screen session ends.
- Warnings only count when the warning actually appears.

If normal WeChat gets blocked:

- Open Brainrotop and check the protected rules.
- Avoid entering WeChat Channels / 视频号 when testing normal chat.
- If normal chat still gets blocked, note what screen you were on so the rule can be adjusted.

## Privacy

Brainrotop is local-only.

It uses accessibility permission to read visible screen information so it can detect short-video surfaces. It stores analytics in private app storage on the phone.

No account, cloud sync, or upload is used.

## Important Note

Brainrotop cannot stop you from manually disabling its accessibility service. It works best as a friction tool: it interrupts the loop quickly, gives you a clean stopping point, and helps you notice your patterns.
