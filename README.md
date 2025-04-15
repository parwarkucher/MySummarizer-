# MySummarizer

MySummarizer is an Android application that allows users to generate concise summaries of YouTube videos using AI. The app extracts captions from YouTube videos and uses Large Language Models (via OpenRouter API) to create both short bullet-point summaries and detailed comprehensive summaries.

## Features

- **YouTube Video Summarization**: Enter a YouTube URL to extract and summarize video content
- **Dual Summary Format**: Get both short bullet-point summaries and detailed comprehensive summaries
- **Chat With AI About Videos**: Discuss the video content with an AI assistant that has context of the video
- **Multiple AI Model Support**: Choose from several AI models via OpenRouter
- **Share Integration**: Receive YouTube links from other apps
- **Markdown Rendering**: Nicely formatted summaries with proper markdown support
- **Retry Mechanism**: Smart retry logic for handling API rate limits and errors
- **Token Usage Tracking**: Monitor your API usage

## Requirements

- Android 6.0 (API level 23) or higher
- Internet connection
- OpenRouter API key (obtain from [OpenRouter](https://openrouter.ai/))

## Setup and Usage

1. **Install the App**: Download and install the MySummarizer app on your Android device
2. **Enter OpenRouter API Key**: Input your OpenRouter API key in the designated field
3. **Select AI Model**: Choose one of the available AI models from the dropdown
4. **Enter YouTube URL**: Paste a YouTube video URL that has captions/subtitles
5. **Generate Summary**: Tap the "Summarize" button to process the video
6. **View Summaries**: Toggle between short and detailed summaries
7. **Chat About Video**: Use the chat button to discuss the video with the AI assistant

## How It Works

1. The app extracts the video ID from the YouTube URL
2. It fetches the caption/subtitle track using web scraping techniques
3. The captions are sent to the selected AI model via OpenRouter API
4. The AI generates two types of summaries:
   - A concise bullet-point summary covering main ideas
   - A detailed comprehensive summary with key points and examples
5. The formatted summaries are displayed with proper markdown rendering
6. The summaries and transcript are stored in memory for context-aware chat

## Technical Details

- Built with Kotlin for Android
- Uses Retrofit for API communication
- Implements Hilt for dependency injection
- Utilizes Coroutines for asynchronous operations
- Implements Material Design UI components
- Uses Markwon for Markdown rendering
- Employs MVVM architecture pattern

## Privacy and Data Handling

- **No YouTube API Key Required**: The app uses web scraping to extract captions
- **API Key Security**: Your OpenRouter API key is stored locally on your device
- **No Video Content Storage**: Video captions and summaries are stored in memory only
- **No External Data Collection**: The app does not collect or share your data

## License

MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Feel free to submit issues or pull requests.

## Acknowledgments

- OpenRouter for providing access to various AI models
