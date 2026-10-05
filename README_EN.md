<div align="center">

# MindMan

**A little space to talk, reflect, and understand yourself.**

AI conversations, mood journaling, psychology reading, and relaxing sounds in one place.

[中文](./README.md) · [User guide (Chinese)](./docs/user-guide.md) · [Setup and deployment (Chinese)](./docs/developer-guide.md)

</div>

![MindMan home](./docs/screenshots/current/home.png)

## What you can do

| Space | Features |
| --- | --- |
| Home | Review recent entries and find a place to start. |
| Listening space | Talk with AI, inspect emotional cues, revisit or archive sessions, and summarize a conversation. |
| Emotion garden | Turn daily mood entries into flowers, edit entries, and request an AI reflection with evidence-based reference scores. |
| Psychology reading | Browse and search articles, visit original sources, and bring an article into chat for discussion or translation. |
| Relaxation | Choose ambient sounds or generated noise and control playback. |

## AI with context

AI can reflect on your current message or garden entry. When you explicitly request a garden review, it can reference up to 14 entries from the past 30 days.

Reference scores are estimates based on the submitted text, not clinical measurements. Scores can be absent when the text does not provide enough evidence. Your own ratings and AI estimates should be interpreted separately.

Article discussions use the content actually stored in MindMan. External articles may contain only feed excerpts; translation and analysis of an excerpt do not cover the full original article.

## Preview

Screenshots show the current frontend with demonstration accounts, entries, articles, and conversation text. They are not private user data or model evaluation results.

### Emotion garden

![Emotion garden](./docs/screenshots/current/garden.png)

### Listening space

![Listening space](./docs/screenshots/current/chat.png)

| Reading | Relaxation |
| --- | --- |
| ![Reading](./docs/screenshots/current/articles.png) | ![Relaxation](./docs/screenshots/current/relax.png) |

## Getting started

There is currently no verified public demo URL. Deploy the application, register an account, and start with a mood entry or a conversation.

AI features require a reachable cloud model or local Ollama service. A cloud-hosted website can connect to a model running on a personal computer, but this needs additional secure network configuration. AI features become unavailable when that computer is offline or asleep.

See the [setup guide](./docs/developer-guide.md), [deployment guide](./deploy/部署手册.md), and [optional Python Agent guide](./code/python-agent/README.md).

[Version 1.1.0 release notes](./docs/releases/v1.1.0.md) and a [free Render frontend preview configuration](./deploy/render-preview.md) are included. Preview mode is labeled and does not connect to a real model or cloud database.

## Your data

Records and conversations are managed by account. Administrators can access records through the management console. Cloud model requests send the selected content to the configured model provider; avoid submitting unnecessary sensitive information.

MindMan supports daily reflection and companionship. AI output can be incorrect and does not provide medical diagnosis or replace professional care.

## Project and feedback

Built with Vue 3 and Java / Spring Boot, with an optional Python garden reflection service. Supports OpenAI-compatible providers and local Ollama.

[Report an issue](https://github.com/OwenWhw/MindMan-MentalHealth-Assistant/issues) with reproduction steps and screenshots with personal information removed.

[MIT License](./LICENSE)
