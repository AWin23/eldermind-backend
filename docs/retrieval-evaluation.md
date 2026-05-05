# 🧪 Retrieval Evaluation Methodology

This document describes how ElderMind evaluates retrieval quality independently of the LLM.

The goal is to ensure that the retrieval system is:
- **accurate**
- **consistent**
- **measurable**

This allows improvements to be driven by data rather than intuition.

---

# 🎯 Why Evaluation Matters

Debug logs explain *why* documents were retrieved, but they do not answer:

> **Was the retrieval actually correct?**

To address this, ElderMind includes an offline evaluation framework that measures retrieval performance using a test dataset.

---

# 📊 Test Dataset

The evaluation system uses a curated dataset of queries, where each query is paired with one or more expected document IDs from the lore corpus.

### Example

```json
{
  "query": "What happened at Red Mountain?",
  "expectedDocs": ["uesp-red-mountain-001"]
}