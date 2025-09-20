# Dark Pattern Detection on Common Crawl

Detect and quantify **dark pattern** phrases (e.g., “cancel anytime”, “hidden fees”) across real web pages using **Apache Spark** on **Common Crawl** WARC data. The pipeline parses WARCs, extracts HTML, searches for known phrases, and reports per-domain metrics. Built in **Scala/Spark**, deployable to YARN with a single `spark-submit`.

---

## ✨ Highlights
- Parses WARC response records and extracts `(url, html, domain)` at scale
- Two modes:
  - `--mode any` → flag page if **any** phrase appears
  - `--mode by-pattern` → per-phrase counts and rates
- Outputs CSVs with **domain hit rates** and **phrase frequencies**
- Designed for **cluster use** (HDFS paths, YARN queues) and linear scaling on segments

---

## 🗂 Data & Assumptions
- **Corpus:** Common Crawl `CC-MAIN-2021-17` (10 consecutive WARCs used in sample)
- **Cluster path:** `hdfs:///single-warc-segment/CC-MAIN-20210410105831-20210410135831-00000..00009.warc.gz`
- **Language:** mixed, majority EN; HTML ≈ 70% of HTTP responses in this slice

---

## 🛠️ Build

This is a standard sbt assembly workflow that produces a fat-jar:

```bash
# from repo root
sbt clean assembly
# yields e.g.
target/scala-2.12/RUBigDataApp-assembly-1.0.jar
```
---

## 🚀 Run on YARN

```bash

# ANY-PATTERN MODE: flag pages containing ANY known phrase
spark-submit \
  --master yarn --deploy-mode cluster \
  --queue silver \
  --class org.rubigdata.RUBigDataApp \
  target/scala-2.12/RUBigDataApp-assembly-1.0.jar \
  --mode any

# BY-PATTERN MODE: per-phrase counts per domain
spark-submit \
  --master yarn --deploy-mode cluster \
  --queue silver \
  --class org.rubigdata.RUBigDataApp \
  target/scala-2.12/RUBigDataApp-assembly-1.0.jar \
  --mode by-pattern

```
---

## 📦 Outputs

- **Domain summary (any-pattern)**  
  `domain, dark_pattern_pages, total_pages, pct_flagged`
- **Per-phrase summary (by-pattern)**  
  `domain, pattern, pages_flagged, total_pages, pct`
- CSVs written to HDFS/local (use `coalesce(1)` for single-file exports).
- Keep YARN logs / History Server screenshots with each run for auditability.

---

## 📊 Sample Results (10 WARCs)

- Pages scanned: **8,275,927**  
- Pages flagged (any-pattern): **146,182** (**1.77%**)  
- Distinct domains hit: **29,354**

Top phrases by share of all hits:
- **cancel anytime** ~ **41.7%**  
- **hidden fees** ~ **32.9%**  
- **subscribe now** ~ **16.3%**  
- **you will be charged** ~ **6.2%**  
- **no thanks** ~ **2.3%**  
- **i hate** ~ **1.9%**

> Interpretation: subscription cancellation friction and price obfuscation dominate detections.

---

## ⚙️ Architecture (TL;DR)

1. **Read** WARC response records (`parseHTTP=true`)  
2. **Extract** `(url, html)` and derive `domain`  
3. **Match** phrases (mode=`any` or `by-pattern`, case-insensitive)  
4. **Aggregate** counts → per-domain (and per-phrase) metrics  
5. **Write** CSV outputs for analysis and plotting

Core entry point: `org.rubigdata.RUBigDataApp` (Scala/Spark).

---

## 🧪 Reproducibility

- Single fat-jar via **sbt assembly** + one `spark-submit` command
- Fixed list of 10 consecutive WARCs to limit topic drift
- CLI flags document **mode**, **HDFS prefix**, **queue**, etc.
- Save:
  - result CSVs
  - `yarn logs -applicationId <id>`
  - History Server DAG/executor screenshots

---

## 🚧 Limitations & Roadmap

- Context-agnostic string match → consider DOM-scoped search (`<button>`, `<a>`, consent banners)
- English-only phrase list → add translation + language detection
- Expand segments; weight phrases to build a **domain “shadiness index”**
- Optional NLP: de-dup near-duplicate pages; sentence-level matching

---

## 📂 Project Structure

- `src/main/scala/org/rubigdata/RUBigDataApp.scala` – Spark app entry; modes & regex logic  
- `Big_Data_project.pdf` – project report (methods, runs, results)  
- `README.md` – this file  
