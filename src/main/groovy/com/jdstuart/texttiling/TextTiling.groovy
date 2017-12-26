package com.jdstuart.texttiling

import com.jdstuart.texttiling.struct.Text
import org.apache.tika.Tika

/**
 * TODO Clean this up:

 * - Remove duck typing.
 * - Replace `println`s with logging statements.
 * - Remove hard-coded file path in main() method.

 * @author Jesse Stuart
 */
class TextTiling {
    Text text

    final static Integer WINDOW_SIZE = 100
    final static Integer STEP_SIZE = 10
    final static Integer SMOOTHING = 3

    def similarityScores = []
    def scoreOffsets = []

    def depthScores = []

    def pseudoBoundaries = []
    def segmentOffsets = []

    TextTiling(String text) {
        this.text = new Text(text).analyze()
    }

    public List<String> segment() {
        this.reset()

        computeSimilarityScores()
        computeDepthScores()
        identifyBoundaries()

        def segments = []
        for (Integer i = 0; i < segmentOffsets.size(); i++) {
            Integer startOffset = text.offsets[newBoundaries[i]] + 1
            Integer endOffset = text.offsets[newBoundaries[i - 1] + 1]
            if (i == 0) {
                segments << text.source.substring(0, startOffset).trim()
            } else if (i == segmentOffsets.size() - 1) {
                segments << text.source.substring(endOffset).trim()
            } else {
                segments << text.source.substring(
                  text.offsets[newBoundaries[i - 1] + 1],
                  text.offsets[newBoundaries[i] + 1]
                ).trim()
            }
        }
        return segments
    }

    public List<String> segment(Integer maxSegments) {
        this.reset()

        computeSimilarityScores()
        computeDepthScores()
        identifyBoundaries()

        def segmentDepths = pseudoBoundaries.collect {
          Integer bound -> depthScores[scoreOffsets.indexOf(bound)]
        }
        def newBoundaries = segmentDepths.sort { it.value }
            .drop(pseudoBoundaries.size() - maxSegments)
            .collect { boundary ->
                text.boundaries.sort { (it - boundary.key).abs() }.first()
            }
            .unique()
            .sort()

        println "new boundaries : ${newBoundaries}"
        List segments = []
        for (Integer i = 0; i < newBoundaries.size(); i++) {
            Integer startOffset = text.offsets[newBoundaries[i]] + 1
            Integer endOffset = text.offsets[newBoundaries[i - 1] + 1]
            if (i == 0) {
                segments << text.source.substring(0, startOffset).trim()
            } else if (i == segmentOffsets.size() - 1) {
                segments << text.source.substring(endOffset).trim()
            } else {
                segments << text.source.substring(
                  text.offsets[newBoundaries[i-1]+1],
                  text.offsets[newBoundaries[i]+1]
                ).trim()
            }

            if (!segments[-1]) {
                println 'Removing empty segment.'
                segments.remove(segments.size() - 1)
            }
        }
        return segments
    }

    private void reset() {
        [
          this.similarityScores,
          this.scoreOffsets,
          this.depthScores,
          this.pseudoBoundaries,
          this.segmentOffsets,
        ]*.clear()
    }

    private void computeSimilarityScores() {
        def tokens = text.stems
        def (left, right) = [ [:], [:] ]
        def (scores, tokOffset) = [ [], [] ]

        // Initialize vector within window
        (0..WINDOW_SIZE).each { i -> incrementTerm(i, left) }
        (WINDOW_SIZE..WINDOW_SIZE*2).each { i -> incrementTerm(i, right) }

        Integer stepCount = 0
        (WINDOW_SIZE..<(tokens.size()-WINDOW_SIZE)).each { Integer i ->
            if (stepCount == 0 || (i == tokens.size()-WINDOW_SIZE-1)) {
                // compute similarity score between the term vectors
                scores << cosineSimilarity(left, right)
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
        (0..(scores.size()-SMOOTHING)).each { Integer i ->
            similarityScores[i] = (scores.drop(i).take(SMOOTHING).sum() / SMOOTHING) // todo can probably use collection.sublist()
            scoreOffsets[i] = tokOffset[i+1]
        }
    }

    private Double cosineSimilarity(Map m1, Map m2) {
        // Compute the squared sum for each vector
        Integer squaredSumM1 = m1.values().collect { it * it }.sum()
        Integer squaredSumM2 = m2.values().collect { it * it }.sum()

        // Union terms in both vectors
        def allTerms = [m1, m2]*.keySet().flatten().unique()
        Integer squareSumShared = allTerms.collect { key ->
            if (m1.containsKey(key) && m2.containsKey(key)) { m1[key] * m2[key] }
            else 0
        }.sum()

        return (squareSumShared / Math.sqrt(squaredSumM1 * squaredSumM2))
    }

    private void incrementTerm(Integer i, Map termVector) {
        if (include(i)) {
            termVector[text.stems[i]] = (termVector[text.stems[i]] ?: 0) + 1
        }
    }

    private void decrementTerm(Integer i, Map termVector) {
        if (include(i)) {
            termVector[text.stems[i]] = (termVector[text.stems[i]] ?: 0) - 1
            if (termVector[text.stems[i]] == 0) {
                termVector.remove(text.stems[i])
            }
        }
    }

    private boolean include(Integer i) {
        return text.pos[i].matches( ~/^[NVJ].*/ )
    }

    private void computeDepthScores() {
        def (maxima, deltaLeft, deltaRight) = [0d, 0d, 0d]

        (similarityScores.size()-1..0).each { Integer i ->
            // scan left
            maxima = similarityScores[i]
            for (Integer j = i; j > 0 && similarityScores[j] >= maxima; j--) {
                maxima = similarityScores[j]
            }
            deltaLeft = maxima - similarityScores[i]

            // scan right
            maxima = similarityScores[i]
            for (Integer j = i; j < similarityScores.size() && similarityScores[j] >= maxima; j++) {
                maxima = similarityScores[j]
            }
            deltaRight = maxima - similarityScores[i]

            depthScores[i] = deltaLeft + deltaRight
        }
    }

    private void identifyBoundaries() {
        double depthAverage = depthScores.sum() / depthScores.size()
        double depthVariance = depthScores.collect { double i -> Math.pow(i - depthAverage, 2) }.sum() / depthScores.size()
        double threshold = depthAverage - (Math.sqrt(depthVariance) / 2)

        Integer neighbors = 3
        for (Integer i = 0; i < depthScores.size(); i++) {
            if (depthScores[i] >= threshold) {
                // Candidate boundary; check if nearby area has any larger values
                // `.max()` to avoid underflow.
                Integer fromIndex = [i - neighbors, 0].max()
                // `min()` to avoid overflow.
                Integer toIndex = [i + neighbors, depthScores.size()].min()
                if (depthScores.subList(fromIndex, toIndex).max() == depthScores[i]) {
                    pseudoBoundaries << scoreOffsets[i]
                }
            }
        }
        // Convert pseudo-boundaries Integero true boundaries, by aligning w/
        // nearest sentence boundary.
        segmentOffsets = pseudoBoundaries.collect { Integer pseudoBoundary ->
            text.boundaries.sort { (it - pseudoBoundary).abs() }.first()
        }
    }

    private void examine() {
        println 'Examining text.'
        for (Integer i = 0; i < text.stems.size(); i++) {
            if (text.boundaries.contains(i)) {
                println ''
            }
            if (include(i)) {
                prInteger "${text.stems[i]} "
            }
        }
    }

    static void main(String[] args) {
//        def f = new File('src/test/resources/sample.txt')
//        def tt = new TextTiling(f.text)
//        def tt = new TextTiling(new File('src/test/resources/sample2.txt').text)
//        def segments = new TextTiling(new File('src/test/resources/Harman.txt').text).segment(10)
//        def tt = new TextTiling(new File("src/test/resources/Harman.txt").text)
        def s = new Tika().parseToString(new File("/Users/jestuart/Downloads/SilverSpring-Datasheet-Communications-Modules.pdf"))
        def tt = new TextTiling(s)


//        def defaultSegments = tt.segment()
//        defaultSegments.each { println "="*20; println it }
//        println "${defaultSegments.size()}"

        def newSegments = tt.segment(10)
        newSegments.each { println "="*20; println it }
        println "${newSegments.size()}"
    }
}
