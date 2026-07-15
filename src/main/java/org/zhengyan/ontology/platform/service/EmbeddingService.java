package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "shall",
            "should", "may", "might", "must", "can", "could", "this", "that",
            "these", "those", "i", "you", "he", "she", "it", "we", "they",
            "my", "your", "his", "her", "its", "our", "their", "mine", "yours",
            "and", "or", "but", "if", "because", "as", "until", "while", "of",
            "at", "by", "for", "with", "about", "against", "between", "into",
            "through", "during", "before", "after", "above", "below", "to",
            "from", "up", "down", "in", "out", "on", "off", "over", "under",
            "again", "further", "then", "once", "here", "there", "when", "where",
            "why", "how", "all", "each", "every", "both", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "also", "much"
    );

    public Map<String, Double> computeTf(String text) {
        List<String> tokens = tokenize(text);
        Map<String, Double> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1.0, Double::sum);
        }
        int maxFreq = tf.values().stream().max(Double::compare).orElse(1.0).intValue();
        tf.replaceAll((k, v) -> 0.5 + 0.5 * (v / maxFreq));
        return tf;
    }

    public Map<String, Double> computeIdf(List<String> allDocs) {
        Map<String, Integer> df = new HashMap<>();
        for (String doc : allDocs) {
            Set<String> uniqueTerms = new HashSet<>(tokenize(doc));
            for (String term : uniqueTerms) {
                df.merge(term, 1, Integer::sum);
            }
        }
        int n = allDocs.size();
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : df.entrySet()) {
            idf.put(entry.getKey(), Math.log((double) n / (1 + entry.getValue())));
        }
        return idf;
    }

    public List<TokenizedDoc> buildVectors(List<String> allDocs) {
        Map<String, Double> idf = computeIdf(allDocs);
        List<TokenizedDoc> vectors = new ArrayList<>();
        for (String doc : allDocs) {
            Map<String, Double> tf = computeTf(doc);
            Map<String, Double> tfidf = new HashMap<>();
            for (Map.Entry<String, Double> entry : tf.entrySet()) {
                String term = entry.getKey();
                double idfVal = idf.getOrDefault(term, 0.0);
                if (idfVal > 0) {
                    tfidf.put(term, entry.getValue() * idfVal);
                }
            }
            vectors.add(new TokenizedDoc(doc, normalize(tfidf)));
        }
        return vectors;
    }

    public Map<String, Double> queryVector(String query, Map<String, Double> idf) {
        Map<String, Double> tf = computeTf(query);
        Map<String, Double> tfidf = new HashMap<>();
        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            String term = entry.getKey();
            double idfVal = idf.getOrDefault(term, 0.0);
            if (idfVal > 0) {
                tfidf.put(term, entry.getValue() * idfVal);
            }
        }
        return normalize(tfidf);
    }

    public double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        Set<String> intersection = new HashSet<>(a.keySet());
        intersection.retainAll(b.keySet());
        double dotProduct = 0;
        for (String term : intersection) {
            dotProduct += a.get(term) * b.get(term);
        }
        return dotProduct;
    }

    public List<String> chunkText(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            if (current.length() + trimmed.length() + 1 > maxChunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (trimmed.length() > maxChunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                int start = 0;
                while (start < trimmed.length()) {
                    int end = Math.min(start + maxChunkSize, trimmed.length());
                    int splitAt = end;
                    if (end < trimmed.length()) {
                        int lastSpace = trimmed.lastIndexOf(' ', end);
                        if (lastSpace > start + maxChunkSize / 2) {
                            splitAt = lastSpace;
                        }
                    }
                    chunks.add(trimmed.substring(start, splitAt).trim());
                    start = splitAt;
                }
            } else {
                if (current.length() > 0) current.append("\n\n");
                current.append(trimmed);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    public Map<String, Double> buildIdfFromChunks(List<String> docTexts) {
        return computeIdf(docTexts);
    }

    public List<ScoredResult> search(String queryText, List<String> chunkTexts, int topK) {
        List<TokenizedDoc> vectors = buildVectors(chunkTexts);
        Map<String, Double> idf = computeIdf(chunkTexts);
        Map<String, Double> queryVec = queryVector(queryText, idf);
        List<ScoredResult> results = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            double sim = cosineSimilarity(queryVec, vectors.get(i).vector);
            results.add(new ScoredResult(i, chunkTexts.get(i), sim));
        }
        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results.stream().filter(r -> r.score > 0).limit(topK).collect(Collectors.toList());
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() > 1 && !STOP_WORDS.contains(t))
                .collect(Collectors.toList());
    }

    private Map<String, Double> normalize(Map<String, Double> vector) {
        double magnitude = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
        if (magnitude == 0) return vector;
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : vector.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / magnitude);
        }
        return normalized;
    }

    public record TokenizedDoc(String text, Map<String, Double> vector) {}
    public record ScoredResult(int index, String text, double score) {}
}
