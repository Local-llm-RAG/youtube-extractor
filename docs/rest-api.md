# REST API

All endpoints are documented in Swagger at `/swagger` when the app is running. The list below is a quick inventory.

## ArXiv

- `POST /api/arxiv/search` — Search ArXiv papers.

## Channels (YouTube)

- `GET /api/channels/mass` — Fetch videos from multiple channels.
- `GET /api/channels/videos` — Fetch videos for a single channel.
- `GET /api/channels/video` — Fetch a single video by URL.

## Cost estimation

- `POST /api/estimate/youtube` — GPT cost estimate for a YouTube video.
- `POST /api/estimate/youtube/channel` — GPT cost estimate for a channel.
- `POST /api/estimate/arxiv` — GPT cost estimate for an ArXiv paper.

## Embedding

- `POST /api/embed` — Debug embedding endpoint. Uses defaults from `EmbeddingProperties`.

## GPT transformations

Handled by `GPTTransformationController` in `com.data.openai.api`. See Swagger for the current shape.
