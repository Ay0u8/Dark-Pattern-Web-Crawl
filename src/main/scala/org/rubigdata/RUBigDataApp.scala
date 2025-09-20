package org.rubigdata

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import java.util.regex.Pattern

object RUBigDataApp {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder
      .appName("Dark Patterns Detector")
      .getOrCreate()
    import spark.implicits._

    val numberOfFiles = 10

    val prefix = "hdfs:///single-warc-segment/" +
      "CC-MAIN-20210410105831-20210410135831-"

    val warcPaths: Seq[String] = (0 until numberOfFiles).map { i =>
      f"$prefix${i}%05d.warc.gz"
    }

    val warcs = warcPaths.map { path =>
      spark.read
        .format("org.rubigdata.warc")
        .option("path", path)
        .option("parseHTTP", "true")
        .option("warcType", "response")
        .load()
    }.reduce(_ union _)

    val df = warcs
      .select($"warcTargetUri".as("url"), $"httpBody".as("html"))
      .withColumn("domain", regexp_extract($"url", """https?://([^/]+)""", 1))

    val mode =
      if (args.contains("--mode") && args.last == "by-pattern") "by-pattern"
      else "any"

    val patterns = Seq(
      "subscribe now", "cancel anytime",
      "hidden fees",    "no thanks",
      "i hate",         "you will be charged"
    )

    if (mode == "any") {
      val regex = patterns.map(p => s"(?i)${Pattern.quote(p)}").mkString("|")
      df.filter(lower($"html").rlike(regex))
        .groupBy("domain")
        .agg(
          count("*").as("dark_pattern_pages"),
          count(lit(1)).as("total_pages")
        )
        .withColumn("pct_flagged", $"dark_pattern_pages" / $"total_pages")
        .orderBy(desc("pct_flagged"))
        .show(50, false)
    } else {
      val perPattern = patterns.map { pat =>
        val lc = pat.toLowerCase
        df.filter(lower($"html").contains(lc))
          .select($"domain", $"url")
          .withColumn("pattern", lit(pat))
      }
      perPattern.reduce(_ union _)
        .groupBy("domain", "pattern")
        .agg(countDistinct($"url").as("pages_flagged"))
        .join(
          df.groupBy("domain").agg(count("*").as("total_pages")),
          "domain"
        )
        .withColumn("pct", round($"pages_flagged" / $"total_pages", 3))
        .orderBy(desc("pct"), asc("pattern"))
        .show(100, false)
    }

    spark.stop()
  }
}

