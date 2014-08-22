package com.jdstuart.texttiling.struct

import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline.Annotation
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import edu.stanford.nlp.util.CoreMap

/**
 * Created by jstuart on 8/21/14.
 */
class Text {
    String text

    def boundaries = []
    def offsets = []

    def tokens = []
    def pos = []
    def stems = []

    Text(text) {
        this.text = text
    }

    def analyze() {
        StanfordCoreNLP annotator = NLPFactory.annotator()
        Annotation document = new Annotation(text)
        annotator.annotate(document)
        def sentences = document.get(CoreAnnotations.SentencesAnnotation.class)

        int tokIndex = 0
        [boundaries, offsets].each { it << 0 } // initialize boundaries
        sentences.each { CoreMap sentence ->
            sentence.get(CoreAnnotations.TokensAnnotation.class).each { CoreLabel tok ->
                tokens << tok.word().toLowerCase()
                pos << tok.tag()
                stems << tok.lemma().toLowerCase()
                offsets << tok.beginPosition()
                tokIndex++
//                println "${tok.word()} ${tok.lemma()} ${tok.tag()} ${tok.beginPosition()} $tokIndex"
            }
            boundaries << tokIndex
        }
        return this
    }

    class NLPFactory {
        private static StanfordCoreNLP pipeline
        private StanfordCoreFactory() { }
        static StanfordCoreNLP annotator() {
            if (pipeline == null) {
                Properties props = new Properties();
                props.put("annotators", "tokenize, ssplit, pos, lemma");
                pipeline = new StanfordCoreNLP(props);
            }
            return pipeline;
        }
    }
}
