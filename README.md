# 🧠 ElderMind Backend

**Elder Scrolls Lore AI Assistant (Spring Boot)**

ElderMind is a backend service for an AI-powered Elder Scrolls lore assistant. The system is designed to provide **accurate, grounded lore explanations** while maintaining flexibility, low complexity, and production-oriented architecture.

This repository contains the **Spring Boot backend** responsible for:

* Chat orchestration and prompt assembly
* Lore retrieval and grounding logic (RAG-lite prototype)
* OpenAI API integration
* Explainable retrieval decisions and safe fallback behavior

The project intentionally evolves in stages to mirror how real-world AI systems are built:

> **simple first → observable always → scalable later**

---

## 🏗️ Architecture Overview

At a high level, the backend is split into four layers:

### API / Controller Layer

* Accepts chat requests from the frontend
* Forwards requests to orchestration logic
* Remains stateless (frontend sends full message history)

### Orchestration Layer

* Coordinates retrieval, prompt assembly, and LLM execution
* Decides whether to use lore grounding or fall back to chat-only mode
* Centralizes AI decision-making logic

### Lore Intelligence Layer (RAG-lite)

* Loads a curated lore corpus
* Scores documents against user queries
* Applies relevance gating to prevent weak or unrelated evidence

### LLM Gateway

* Handles all OpenAI SDK calls
* Keeps OpenAI integration isolated and replaceable

---

## 🌿 Branches Explained

This repository contains **two important backend branches**, each serving a distinct purpose.

---

## `main` branch — Chat-Only Lore Assistant (Stable)

The `main` branch represents the **stable, production-ready baseline** of ElderMind.

### What it does

* Uses OpenAI as a conversational engine
* Injects structured system prompts to enforce:

  * Lore scholar tone
  * Canon-aware responses
  * In-character persona modes
* Relies on the model’s general knowledge (no external retrieval)

### Why this branch exists

* Establishes a clean baseline architecture
* Keeps the system simple and reliable
* Serves as a fallback if experimental features are removed
* Demonstrates prompt engineering, orchestration, and API design

### Key characteristics

* No external lore corpus
* No retrieval or ranking
* Minimal infrastructure
* Strong separation of concerns

This branch answers the question:

> **“What does ElderMind look like without retrieval complexity?”**

---

## `rag` branch — RAG-Lite Lore Intelligence Prototype

The `rag` branch is an **experimental prototype** that introduces retrieval-augmented generation (RAG-lite) while intentionally avoiding premature infrastructure.

### What it adds

* A curated Elder Scrolls lore corpus (JSON-based)
* Keyword-based document scoring and ranking
* Relevance gating to decide when retrieval is trustworthy
* Prompt grounding using retrieved evidence snippets
* `RetrievalDecision` telemetry for observability and debugging

### RAG-Lite philosophy

This branch follows a **RAG-lite approach**:

* Retrieval is **optional**, not mandatory
* Evidence is injected **only when confidence is high**
* If retrieval confidence is low, the system falls back to chat-only mode
* No embeddings, vector databases, or search infrastructure (yet)

This mirrors real production evolution:

> **heuristics → observability → semantics → scale**

---

## 🔍 Retrieval Decision Transparency (Observability)

A core feature of the `rag` branch is **explainable retrieval behavior**.

Each request produces a `RetrievalDecision` object that records:

* Whether retrieval was attempted
* Whether evidence was injected
* Number of matched documents
* Top relevance score
* Gating threshold used
* Explicit fallback reason (e.g. `NO_MATCHES`, `BELOW_THRESHOLD`)
* Retriever version

These decisions are logged server-side, making it easy to answer:

* Why wasn’t retrieval used?
* Was evidence too weak?
* Is the corpus missing content?

This prevents silent failures and reduces hallucination risk.

---

## 📚 Lore Corpus (RAG Branch)

* Stored as structured JSON during the prototype phase
* Each entry represents a **single atomic lore concept**
* Short, paragraph-sized snippets (not full articles)
* Includes metadata such as:

  * ID
  * Source (UESP, in-game book, etc.)
  * Title
  * Text

This design:

* Keeps token usage low
* Makes conflicts and uncertainty explicit
* Enables future upgrades (DB, embeddings, metadata filters)

---

## 🚫 Explicit Non-Goals (for now)

The backend intentionally does **not** include:

* Vector databases or embeddings
* Elasticsearch / BM25
* Automatic corpus ingestion
* Persistent user accounts
* Stored chat history

These are **future upgrade paths**, not missing features.

---

## ▶️ How to Run (Java / Spring Boot)

### Prerequisites

* **Java 17**
* **Gradle** (or use the included Gradle wrapper)
* An **OpenAI API key**

### Environment Variables

Set your OpenAI API key:

```bash
export OPENAI_API_KEY=your_api_key_here
```

(On Windows PowerShell:)

```powershell
setx OPENAI_API_KEY "your_api_key_here"
```

### Run the Backend

From the project root:

```bash
./gradlew bootRun
```

Or on Windows:

```bash
gradlew bootRun
```

The server will start on:

```
http://localhost:8080
```

---

## 🛠️ Tech Stack

* **Java 17**
* **Spring Boot**
* **OpenAI Java SDK**
* JSON-based lore corpus (prototype)
* Stateless REST API

---
