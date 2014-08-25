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

    def depthScores = []

    def segmentOffsets = []

    TextTiling(String text) {
        this.text = new Text(text).analyze()
    }

    def computeSimilarityScores() {
        def tokens = text.stems
        def (left, right) = [ [:], [:] ]
        def (scores, tokOffset) = [ [], [] ]

        // Initialize vector within window
        (0..WINDOW_SIZE).each { i -> incrementTerm(i, left) }
        (WINDOW_SIZE..WINDOW_SIZE*2).each { i -> incrementTerm(i, right) }

        int stepCount = 0
        (WINDOW_SIZE..<(tokens.size()-WINDOW_SIZE)).each { int i ->
            if (stepCount == 0 || (i == tokens.size()-WINDOW_SIZE-1)) {
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
        (0..(scores.size()-SMOOTHING)).each { int i ->
            similarityScores[i] = (scores.drop(i).take(SMOOTHING).sum() / SMOOTHING) // todo can probably use collection.sublist()
            scoreOffsets[i] = tokOffset[i+1]
        }
    }

    def cosineSimilarity(Map m1, Map m2) {
        // Compute the squared sum for each vector
        int squaredSumM1 = m1.values().collect { it * it }.sum()
        int squaredSumM2 = m2.values().collect { it * it }.sum()

        // Union terms in both vectors
        def allTerms = [m1, m2]*.keySet().flatten().unique()
        int squareSumShared = allTerms.collect { key ->
            if (m1.containsKey(key) && m2.containsKey(key)) { m1[key] * m2[key] }
            else 0
        }.sum()

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
            if (termVector[text.stems[i]] == 0) {
                termVector.remove(text.stems[i])
            }
        }
    }

    boolean include(int i) {
        return text.pos[i].matches( ~/^[NVJ].*/ )
//        return true
    }

    def computeDepthScores() {
        def (maxima, deltaLeft, deltaRight) = [0d, 0d, 0d]

        (similarityScores.size()-1..0).each { int i ->
            // scan left
            maxima = similarityScores[i]
            for (int j = i; j > 0 && similarityScores[j] >= maxima; j--) {
                maxima = similarityScores[j]
            }
            deltaLeft = maxima - similarityScores[i]

            // scan right
            maxima = similarityScores[i]
            for (int j = i; j < similarityScores.size() && similarityScores[j] >= maxima; j++) {
                maxima = similarityScores[j]
            }
            deltaRight = maxima - similarityScores[i]

            depthScores[i] = deltaLeft + deltaRight
        }
    }

    def identifyBoundaries() {
        double depthAverage = depthScores.sum() / depthScores.size()
        double depthVariance = depthScores.collect { double i -> Math.pow(i - depthAverage, 2) }.sum() / depthScores.size()
        double threshold = depthAverage - (Math.sqrt(depthVariance) / 2)

        def pseudoBoundaries = []
        int neighbors = 3
        for (int i = 0; i < depthScores.size(); i++) {
            if (depthScores[i] >= threshold) {
                // Candidate boundary; check if nearby area has any larger values
                int fromIndex = [i-neighbors, 0].max()
                int toIndex = [i+neighbors, depthScores.size()].min()
                if (depthScores.subList(fromIndex, toIndex).max() == depthScores[i]) {
                    pseudoBoundaries << scoreOffsets[i]
                }
            }
        }
        // Convert pseudo-boundaries into true boundaries, by aligning w/ nearest sentence boundary
        segmentOffsets = pseudoBoundaries.collect { int pseudoBoundary ->
            text.boundaries.sort { (it - pseudoBoundary).abs() }.first()
        }
    }

    def printSegments() {
        for (int i = 0; i < segmentOffsets.size(); i++) {
            if (i == 0) { // first segment
                println "="*20 + " first segment"
                println text.source.substring(0, text.offsets[segmentOffsets[i]]+1).trim()
            }
            else if (i == segmentOffsets.size()-1) { // last segment
                println "="*20 + " last segment"
                println text.source.substring(text.offsets[segmentOffsets[i-1]+1]).trim()
            }
            else { // everything else
                println "="*20 + " segment ${segmentOffsets[i]}"
                println text.source.substring(text.offsets[segmentOffsets[i-1]+1], text.offsets[segmentOffsets[i]+1]).trim()
            }
        }
    }

    def examine() {
        println "Examining text."
        for (int i = 0; i < text.stems.size(); i++) {
            if (text.boundaries.contains(i)) {
                println ""
            }
            if (include(i)) {
                print "${text.stems[i]} "
            }
        }
    }

    static void main(String[] args) {
//        def f = new File('src/test/resources/sample.txt')
//        def tt = new TextTiling(f.text)
//        def tt = new TextTiling(new File('src/test/resources/sample2.txt').text)
        def tt = new TextTiling(new File('src/test/resources/Harman.txt').text)
        tt.computeSimilarityScores()
        tt.computeDepthScores()
        tt.identifyBoundaries()
        tt.printSegments()

        println "sim scores: "
        tt.similarityScores.each { println it }
        println ""
        tt.depthScores.each { println it }

        println tt.segmentOffsets
        println "num segments : ${tt.segmentOffsets.size()+1}"

        tt.examine()
    }
}
