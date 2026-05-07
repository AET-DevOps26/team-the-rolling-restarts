# Problem Statement

## 1. Problem Statement

### Main problem

Users are overwhelmed by the volume of online news and struggle to quickly find, understand, and prioritise information that is actually relevant to them. Traditional news platforms either provide unfiltered feeds or basic recommendations that do not adapt well to user intent, reading behaviour, or comprehension level. As a result, users waste time, miss important updates, or disengage from news consumption altogether.

---

### Main functionality

The application is a personalised news aggregation platform that:

- Collects articles from multiple sources (publisher RSS feeds, Google News RSS feeds)
- Cleans, normalises, and enriches content (topics, tags, metadata)
- Generates personalised feeds based on user behaviour and interests
- Provides AI-generated summaries and explanations of news content
- Supports search, filtering, bookmarking, and notifications
- Tracks user interactions to continuously improve recommendations

---

### Intended users

Primary users are:

- University students and young professionals who want fast, relevant updates without information overload
- General users interested in personalised news consumption instead of generic news feeds
- Users with limited time who prefer summaries over full articles
- Users with varying knowledge levels who may need simplified explanations of complex topics

---

### Meaningful integration of GenAI

GenAI is a core component of the system, not an add-on. It is used to:

- Generate concise summaries of articles (multi-length and multi-style)
- Provide simplified explanations of complex topics for different knowledge levels
- Support semantic understanding for categorisation and tagging of articles
- Improve recommendation quality by interpreting user preferences and content meaning beyond keywords
- Optionally detect sentiment and bias to provide additional context to users

This ensures that the system is not just a content aggregator but an intelligent information processing layer.

---

### Example usage scenarios

**Scenario 1: Daily news consumption**  
A user opens the app in the morning. Instead of a raw feed, they see a personalised list of 10–15 articles ranked by relevance. Each article includes a short AI-generated summary. The user can quickly decide what to read in full.

**Scenario 2: Complex topic understanding**  
A user encounters an article about a technical economic policy. They request a simplified explanation. The system generates an easy-to-understand breakdown tailored to a non-expert level.

**Scenario 3: Personalisation over time**  
The system tracks which articles the user reads, ignores, or saves. Over time, recommendations adapt, prioritising topics and formats the user actually engages with.

**Scenario 4: Breaking news notification**  
A user follows specific topics. When relevant breaking news occurs, the system sends a notification with a short AI-generated summary instead of just a headline link.
