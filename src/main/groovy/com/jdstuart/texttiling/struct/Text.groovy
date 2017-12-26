package com.jdstuart.texttiling.struct

import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline.Annotation
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import edu.stanford.nlp.util.CoreMap

/**
 * @author Jesse Stuart
 */
class Text {
    String source

    def boundaries = []
    def offsets = []

    def tokens = []
    def pos = []
    def stems = []

    Text(String text) {
        this.source = text
    }

    def analyze() {
        StanfordCoreNLP annotator = NLPFactory.annotator()
        Annotation document = new Annotation(source)
        annotator.annotate(document)
        def sentences = document.get(CoreAnnotations.SentencesAnnotation.class)

        int tokIndex = 0
        [boundaries, offsets]*.add 0 // initialize boundaries
        sentences.each { CoreMap sentence ->
            sentence.get(CoreAnnotations.TokensAnnotation.class).each { CoreLabel tok ->
                tokens << tok.word().toLowerCase()
                pos << tok.tag()
                stems << tok.lemma().toLowerCase()
                offsets << tok.beginPosition()
                tokIndex++
            }
            boundaries << tokIndex
        }
        return this
    }

    class NLPFactory {
        /**
         * Forbid instantiation.
         */
        private StanfordCoreFactory() {}

        private static StanfordCoreNLP pipeline

        static StanfordCoreNLP annotator() {
            pipeline ?: new StanfordCoreNLP(
                new Properties(annotators: 'tokenize, ssplit, pos, lemma')
            )
        }
    }
}
