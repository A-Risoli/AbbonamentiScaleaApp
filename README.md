# AbbonamentiScaleaApp 🚗✨

AbbonamentiScaleaApp is a friendly Android app for parking checks in Scalea. It reads license plates with the camera **or** lets you type a plate in the textbox, then pings a Telegram bot (via TDLib) to verify the subscription. You get the answer fast and can keep scanning without slowing down.

## What it does 🎯

- Live license plate recognition with the camera
- Manual plate entry via textbox
- Sends requests to a Telegram bot using TDLib
- Instant response with clear status (ok/error)
- Light/Dark theme with a consistent, pleasant palette

## Local setup (required) 🔐

TDLib credentials must stay out of git. Put them in local.properties (ignored by git) using local.properties.example as a template.

Required fields:

- BOT_USERNAME
- API_ID
- API_HASH

## Notes 📝

- Get TDLib credentials from https://my.telegram.org → API development tools.
- If TDLib asks for login, you’ll need to authenticate your Telegram user.
