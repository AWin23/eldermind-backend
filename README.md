# 🧠 ElderMind Backend

**Elder Scrolls Lore AI Assistant (Spring Boot)**

ElderMind is a backend service for an AI-powered Elder Scrolls lore assistant designed to deliver **accurate, grounded responses** while maintaining **system transparency, simplicity, and production-oriented architecture**.

The system evolves in stages to mirror real-world AI development:

> **simple first → observable always → intelligent → scalable**

---

# 🚀 What This Backend Does

This repository contains the Spring Boot backend responsible for:

- Chat orchestration and prompt assembly  
- Hybrid retrieval (keyword + embedding) for lore grounding  
- Retrieval gating and fallback decision logic  
- Confidence scoring for trust signals  
- OpenAI API integration via a gateway layer  
- Retrieval observability and debugging  

---

# 🏗️ Architecture Overview

The system is split into four main layers:

## 1. API / Controller Layer
- Accepts chat requests from the frontend  
- Stateless (frontend sends full message history)  
- Delegates to orchestration logic  

## 2. Orchestration Layer (Core Brain)
- `DefaultLoreOrchestrator`
- Coordinates the full request lifecycle  
- Decides:
  - whether to use retrieval
  - whether to fallback to chat-only mode  
- Centralizes all AI decision-making  

## 3. Lore Intelligence Layer (Hybrid Retrieval)
- Query analysis (keyword extraction / must-hit terms)  
- Hybrid retrieval:
  - keyword-based scoring  
  - embedding-based similarity  
- Blended ranking (hybrid scoring)  
- Relevance gating:
  - threshold check  
  - hard-evidence (must-hit) validation  

## 4. LLM Gateway
- Isolates all OpenAI API calls  
- Ensures clean separation of concerns  
- Makes model integration replaceable  

---

# 🔄 Request Flow

Each user query follows this lifecycle:
User Query
→ ChatController
→ DefaultLoreOrchestrator
→ QueryAnalyzer
→ HybridRetriever
→ Hard-Evidence Gate
→ Pass → Confidence Scoring → Prompt Assembly
→ Fail → Chat-Only Fallback
→ LLM Gateway
→ OpenAI API
→ ChatResponse

---

# 🔍 Hybrid Retrieval System

ElderMind uses a **hybrid retrieval approach**:

### Keyword Retrieval
- Exact term matching  
- Score normalization  
- Strong precision for known entities  

### Embedding Retrieval
- Semantic similarity search  
- Handles natural language queries  
- Improves recall  

### Hybrid Scoring

finalScore = 0.6 * embeddingScore + 0.4 * keywordScore


This allows the system to:
- Capture exact lore matches  
- Understand semantic queries  
- Balance precision and recall  

---

# 🚦 Retrieval Gating (Critical Design Choice)

Retrieval is **not always used**.

The system applies:

### 1. Hard-Evidence Gate
- Ensures key query terms appear in retrieved documents  

### 2. Threshold Check
- Requires minimum relevance score  

If either fails:
→ system falls back to chat-only mode

> **Design principle:**  
> Prefer *no grounding* over *bad grounding*

---

# 📊 Retrieval Confidence Scoring

After retrieval passes gating, the system computes a **confidence score**:

### Based on:
- Top document relevance score  
- Distance above threshold  
- Number of matched documents  

### Produces:
- `retrievalConfidence` (0.0 – 1.0)
- `groundingLabel` (human-readable)

### Example labels:
- **Grounded in canonical sources**
- **Partially grounded in canonical sources**
- **Weak supporting evidence**
- **Answer generated without external evidence**

This enables:
- Transparent AI behavior  
- Debugging and tuning  
- Future UI trust signals  

---

# 🔍 Retrieval Decision Observability

Each request generates a `RetrievalDecision` object:

### Tracks:
- Retrieval attempted  
- Retrieval used  
- Matched document count  
- Top relevance score  
- Threshold used  
- Confidence score  
- Grounding label  
- Fallback reason  
- Retriever version  

This allows you to answer:

- Why wasn’t retrieval used?  
- Was evidence too weak?  
- Is the corpus missing content?  

> Prevents silent failures and reduces hallucination risk.

---

# 🧪 Retrieval Evaluation

The system includes an **offline evaluation runner**:

### Uses:
- Query dataset with expected documents  
- Hit@K metrics  

### Enables:
- Retrieval accuracy measurement  
- Regression testing  
- Comparison across retrieval strategies  

---

# 📚 Lore Corpus

- Stored as structured JSON (prototype phase)  
- Each entry = atomic lore concept  
- Short, paragraph-sized snippets  

### Metadata includes:
- ID  
- Source (UESP, in-game text)  
- Title  
- Text  

### Benefits:
- Low token usage  
- High clarity  
- Easy debugging  
- Future extensibility  

---

# ⚖️ Design Tradeoffs

| Decision | Tradeoff |
|--------|--------|
| Retrieval is optional | Avoids bad grounding, may lose context |
| Hybrid scoring | Better recall, requires tuning |
| JSON corpus | Simple, not scalable |
| Confidence scoring | Improves transparency, not truth guarantee |
| Hard gating | Safer outputs, stricter recall |

---

# 🌿 Branches Explained

## `main` — Chat-Only Assistant (Stable)
- No retrieval  
- Pure prompt engineering  
- Reliable fallback baseline  

## `feature/rag-hybrid` — Hybrid Retrieval System
- Keyword + embedding retrieval  
- Gating logic  
- Confidence scoring  
- Observability + evaluation  

---

# 🚫 Explicit Non-Goals (for now)

- Vector databases (e.g., Pinecone, FAISS)  
- Elasticsearch / BM25  
- Automated corpus ingestion  
- Persistent user accounts  
- Stored chat history  

These are **intentional future steps**, not missing features.

---

# ▶️ How to Run

## Prerequisites
- Java 17  
- Gradle  
- OpenAI API key  

## Set API Key
```bash
export OPENAI_API_KEY=your_api_key_here


## ▶️ Run

```bash
./gradlew bootRun

Server Runs on:
http://localhost:8080


### 🛠️ Tech Stack
- Java 17
- Spring Boot
- OpenAI Java SDK
- JSON-based lore corpus
- Stateless REST API


