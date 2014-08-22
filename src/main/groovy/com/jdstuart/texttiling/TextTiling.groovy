package com.jdstuart.texttiling

import com.jdstuart.texttiling.struct.Text

/**
 *
 * @author Jesse Stuart
 */
class TextTiling {
    Text text

    int WINDOW_SIZE = 100
    int STEP_SIZE = 10
    int SMOOTHING = 3

    def similarityScores = []
    def scoreOffsets = []

    TextTiling(String text) {
        this.text = new Text(text).analyze()
    }

    def computeSimilarityScores() {
        def final tokens = text.stems
        def (left, right) = [ [:], [:] ]
        def (scores, tokOffset) = [ [], [] ]

        (WINDOW_SIZE..0).each { i ->
            incrementTerm(i, left)
        }
        (WINDOW_SIZE*2..WINDOW_SIZE).each { i ->
            incrementTerm(i, right)
        }

        int stepCount = 0
        (WINDOW_SIZE..<(tokens.size()-WINDOW_SIZE)).each { int i ->
            if (stepCount == 0 || (i == tokens.size()-WINDOW_SIZE-1)) {
                println "Calling cosine similarity from window $i"
                println left.sort { it.key }
                println right.sort { it.key }
                // compute similarity score between the term vectors
                scores << cosineSimilarity(left, right) // todo
                tokOffset << i
                // reset step count
                stepCount = STEP_SIZE
            }
            // Update the term vector for the new window:
            // 1) Add word at end of right window, remove word at the end of the left window
            decrementTerm(i - WINDOW_SIZE, left)
            incrementTerm(i + WINDOW_SIZE, right)
            // 2) Add current word to left window & remove it from the right
            incrementTerm(i, left)
            decrementTerm(i, right)

            stepCount--
        }

        println scores
        (0..<scores.size()).each { int i ->
            similarityScores[i] = (scores.drop(i).take(SMOOTHING).sum() / SMOOTHING)
            scoreOffsets = tokOffset[i+1]
        }
        println similarityScores
        println similarityScores.size()
    }

    def cosineSimilarity(Map m1, Map m2) {
        // Compute the squared sum for each vector
        int squaredSumM1 = m1.values().collect { it * it }.sum()
        int squaredSumM2 = m2.values().collect { it * it }.sum()

        // Union terms in both vectors
        def allTerms = [m1, m2]*.keySet().flatten().unique()
        int squareSumShared = allTerms.collect { key ->
            if (m1.containsKey(key) && m2.containsKey(key)) { m1[key] * m2[key] } else 0
        }.sum()
        println "ssM1: $squaredSumM1, ssM2: $squaredSumM2, ssShared: $squareSumShared"
        return (squareSumShared / Math.sqrt(squaredSumM1 * squaredSumM2))
    }

    void incrementTerm(int i, Map termVector) {
        if (include(i)) {
            termVector[text.stems[i]] = (termVector[text.stems[i]] ?: 0) + 1
        }
    }

    void decrementTerm(int i, Map termVector) {
        if (include(i)) {
            termVector[text.stems[i]] = (termVector[text.stems[i]] ?: 0) - 1
        }
    }

    boolean include(int i) {
        return text.pos[i].matches( ~/^[NVJ].*/)
    }

    static void main(String[] args) {
        def f = new File('src/test/resources/sample.txt')
        def tt = new TextTiling(f.text)
        tt.computeSimilarityScores()
        new TextTiling(new File('src/test/resources/sample2.txt').text)
    }
}
